const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HARIOM_UID = "hariom_uid_001";
const KRISHI_UID = "krishi_uid_002";
const INTRUDER_UID = "intruder_uid_999";
const OTHER_COUPLE_UID = "other_couple_777";
const ROOM_CODE = "986607";

// Realistic X.509 SPKI Base64 P-256 public keys (124 chars each)
const HOST_PUB_KEY =
  "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/==";
const PARTNER_PUB_KEY =
  "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzyxwvutsrqponmlkjihgfedcba9876543210ZYXWVUTSRQPONMLKJIHGFEDCBA9876543210+/==";
const MITM_PUB_KEY =
  "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEHACKERKEY00000000000000000000000000000000000000000000000000000000000000000+/==";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRoomPayload(uid = HARIOM_UID, name = "Hariom", pubKey = HOST_PUB_KEY) {
  const now = new Date(Date.now() - 2000);
  return {
    coupleCode: ROOM_CODE,
    creatorUid: uid,
    creatorName: name,
    creatorPublicKey: pubKey,
    partnerUid: "",
    partnerName: "",
    partnerPublicKey: "",
    pendingPartnerUid: "",
    pendingPartnerName: "",
    pendingPartnerPublicKey: "",
    memberUids: [uid],
    spiceLevel: "FLIRTY",
    status: "WAITING",
    blockCount: 1,
    latestBlockHash: "009dc43882abcdef1234567890",
    createdAt: now,
    updatedAt: now,
  };
}

function validBlockPayload(senderUid, senderName, memberUids, idx = 1, actionType = "QUIZ") {
  return {
    coupleCode: ROOM_CODE,
    senderUid,
    senderName,
    memberUids,
    blockIndex: idx,
    actionType,
    blockHash: "00abcdef1234567890abcd" + String(idx).padStart(2, "0"),
    encryptedPacket: "KRIMOON:BCHAIN:eyJ2IjozLCJ0YWciOiJhYmMxMjMifQ==",
    createdAt: new Date(Date.now() - 1000),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write couple rooms or blocks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("coupleRooms").doc(ROOM_CODE).get());
  await assertFails(unauthDb.collection("coupleRooms").get());
  await assertFails(
    unauthDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );
});

test("Zero-Knowledge ECDH Handshake & Host Approval: rejects MITM key swap, self-approval, and premature reads", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  // Host creates unique room with pinned ECDH P-256 public key
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );

  // Invalid/script-injected public key must be rejected
  await assertFails(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: KRISHI_UID,
      pendingPartnerName: "Krishi",
      pendingPartnerPublicKey: "<script>alert(1)</script>",
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 750),
    })
  );

  // Partner 2 CANNOT bypass Host approval and jump straight to CONNECTED
  await assertFails(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      partnerPublicKey: PARTNER_PUB_KEY,
      memberUids: [HARIOM_UID, KRISHI_UID],
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Partner 2 submits join request with ECDH public key -> PENDING_APPROVAL
  await assertSucceeds(krishiDb.collection("coupleRooms").doc(ROOM_CODE).get());
  await assertSucceeds(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: KRISHI_UID,
      pendingPartnerName: "Krishi",
      pendingPartnerPublicKey: PARTNER_PUB_KEY,
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Pending partner CAN observe room status waiting for Host approval
  await assertSucceeds(krishiDb.collection("coupleRooms").doc(ROOM_CODE).get());

  // Pending partner CANNOT self-approve or write encrypted blocks before Host approves
  await assertFails(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      partnerPublicKey: PARTNER_PUB_KEY,
      memberUids: [HARIOM_UID, KRISHI_UID],
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 600),
    })
  );
  await assertFails(
    krishiDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00prematureblock12345")
      .set(validBlockPayload(KRISHI_UID, "Krishi", [HARIOM_UID, KRISHI_UID], 1))
  );

  // 3rd party intruder CANNOT read or hijack a PENDING_APPROVAL room
  await assertFails(intruderDb.collection("coupleRooms").doc(ROOM_CODE).get());
  await assertFails(
    intruderDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: INTRUDER_UID,
      pendingPartnerName: "Hacker",
      pendingPartnerPublicKey: MITM_PUB_KEY,
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 550),
    })
  );

  // Host CANNOT substitute a different public key during approval (MITM guard)
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      partnerPublicKey: MITM_PUB_KEY,
      memberUids: [HARIOM_UID, KRISHI_UID],
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 520),
    })
  );

  // Host approves Partner 2 -> atomically promotes pendingPartnerPublicKey -> partnerPublicKey
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      partnerPublicKey: PARTNER_PUB_KEY,
      memberUids: [HARIOM_UID, KRISHI_UID],
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Post-bonding: neither Host nor Partner can ever swap creatorPublicKey or partnerPublicKey
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      creatorPublicKey: MITM_PUB_KEY,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerPublicKey: MITM_PUB_KEY,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Block count rollback must be rejected
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      blockCount: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Shadow/ghost field injection must be rejected
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      spiceLevel: "SWEET",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Valid spice update succeeds
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      spiceLevel: "SWEET",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // 3rd party intruder permanently locked out of CONNECTED room
  await assertFails(intruderDb.collection("coupleRooms").doc(ROOM_CODE).get());
});

test("Host can decline pending partner and return room to WAITING cleanly", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );
  await assertSucceeds(
    intruderDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: INTRUDER_UID,
      pendingPartnerName: "Stranger",
      pendingPartnerPublicKey: MITM_PUB_KEY,
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Host declines stranger -> clears pending key and returns to WAITING
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      status: "WAITING",
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Encrypted blocks are truly append-only (reject plaintext, mutation, and deletion)", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  const members = [HARIOM_UID, KRISHI_UID];
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );
  await assertSucceeds(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: KRISHI_UID,
      pendingPartnerName: "Krishi",
      pendingPartnerPublicKey: PARTNER_PUB_KEY,
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 800),
    })
  );
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      partnerPublicKey: PARTNER_PUB_KEY,
      memberUids: members,
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 600),
    })
  );

  const blockActions = ["QUIZ", "DARE", "WAVELENGTH", "WHISPER"];
  for (let i = 0; i < blockActions.length; i++) {
    const action = blockActions[i];
    const blockId = `00block_${action.toLowerCase()}_${i + 1}`;
    await assertSucceeds(
      hariomDb
        .collection("coupleRooms")
        .doc(ROOM_CODE)
        .collection("blocks")
        .doc(blockId)
        .set(validBlockPayload(HARIOM_UID, "Hariom", members, i + 1, action))
    );
  }

  // Partner queries blocks with memberUids filter
  await assertSucceeds(
    krishiDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .where("memberUids", "array-contains", KRISHI_UID)
      .get()
  );

  // Unfiltered list queries must fail
  await assertFails(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").get()
  );
  await assertFails(hariomDb.collection("coupleRooms").get());

  // Plaintext packet must fail
  await assertFails(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00badplaintextblock")
      .set({
        ...validBlockPayload(HARIOM_UID, "Hariom", members, 10, "WHISPER"),
        encryptedPacket: "plaintext_unencrypted_whisper",
      })
  );

  // Immutable blocks cannot be updated OR deleted
  await assertFails(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00block_quiz_1")
      .update({ senderName: "Tampered" })
  );
  await assertFails(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00block_quiz_1")
      .delete()
  );

  // Intruder cannot read or write blocks
  await assertFails(
    intruderDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00block_quiz_1")
      .get()
  );
});

test("Single-use room code lock: room cannot be deleted or ever reused by another couple even after unpairing", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const otherCoupleDb = testEnv.authenticatedContext(OTHER_COUPLE_UID).firestore();

  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );

  // Room document deletion is permanently forbidden
  await assertFails(hariomDb.collection("coupleRooms").doc(ROOM_CODE).delete());

  // Unpair room -> terminal UNPAIRED state
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      status: "UNPAIRED",
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Another couple CANNOT reuse or recreate the retired 6-digit code
  await assertFails(
    otherCoupleDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .set(validRoomPayload(OTHER_COUPLE_UID, "Other"))
  );
  await assertFails(
    otherCoupleDb.collection("coupleRooms").doc(ROOM_CODE).get()
  );

  // Original host also cannot reopen an UNPAIRED room
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      spiceLevel: "SWEET",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Same-account 2-phone testing supports pending confirmation and ECDH key promotion", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();

  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload(HARIOM_UID, "Hariom"))
  );

  // Phone B signed into same account submits pending join request with its own ECDH public key
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: HARIOM_UID,
      pendingPartnerName: "Krishi",
      pendingPartnerPublicKey: PARTNER_PUB_KEY,
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Phone A (Host) approves it
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: "",
      partnerName: "Krishi",
      partnerPublicKey: PARTNER_PUB_KEY,
      memberUids: [HARIOM_UID],
      pendingPartnerUid: "",
      pendingPartnerName: "",
      pendingPartnerPublicKey: "",
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  const base64Packet = "KRIMOON:BCHAIN:eyJ2IjozLCJjdCI6ImFiKzEyLzg5LV89PSJ9";
  await assertSucceeds(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00b64packetblock12345")
      .set({
        ...validBlockPayload(HARIOM_UID, "Krishi", [HARIOM_UID], 1, "WAVELENGTH"),
        encryptedPacket: base64Packet,
      })
  );
});
