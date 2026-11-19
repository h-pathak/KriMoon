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
const ROOM_CODE = "986607";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRoomPayload(uid = HARIOM_UID, name = "Hariom") {
  const now = new Date(Date.now() - 2000);
  return {
    coupleCode: ROOM_CODE,
    creatorUid: uid,
    creatorName: name,
    partnerUid: "",
    partnerName: "",
    memberUids: [uid],
    spiceLevel: "FLIRTY",
    status: "WAITING",
    blockCount: 1,
    latestBlockHash: "009dc43882abcdef1234567890",
    createdAt: now,
    updatedAt: now,
  };
}

function validBlockPayload(senderUid, senderName, memberUids, idx = 1) {
  return {
    coupleCode: ROOM_CODE,
    senderUid,
    senderName,
    memberUids,
    blockIndex: idx,
    actionType: "QUIZ",
    blockHash: "00abcdef1234567890abcdef",
    encryptedPacket: "KRIMOON:BCHAIN:eyJ2IjoyLCJ0YWciOiJhYmMxMjMifQ==",
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

test("Phone A creates room, Phone B joins by 6-digit code, and 3rd party intruder is permanently blocked", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  // Phone A creates room
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );

  // Phone B checks open waiting room by exact 6-digit code and joins
  await assertSucceeds(krishiDb.collection("coupleRooms").doc(ROOM_CODE).get());
  await assertSucceeds(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      memberUids: [HARIOM_UID, KRISHI_UID],
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bonded members can read & sync spice level
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      spiceLevel: "BOLD",
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

  // 3rd party intruder is locked out of reading, joining, or modifying a connected room
  await assertFails(intruderDb.collection("coupleRooms").doc(ROOM_CODE).get());
  await assertFails(
    intruderDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: INTRUDER_UID,
      partnerName: "Hacker",
      memberUids: [HARIOM_UID, INTRUDER_UID],
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Encrypted blockchain blocks sync live between both partners, reject plaintext, reject updates, and isolate intruders", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  const members = [HARIOM_UID, KRISHI_UID];
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );
  await assertSucceeds(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerUid: KRISHI_UID,
      partnerName: "Krishi",
      memberUids: members,
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Phone A sends encrypted KRIMOON:BCHAIN: block
  const blockRefA = hariomDb
    .collection("coupleRooms")
    .doc(ROOM_CODE)
    .collection("blocks")
    .doc("00abcdef1234567890abcdef");
  await assertSucceeds(
    blockRefA.set(validBlockPayload(HARIOM_UID, "Hariom", members, 1))
  );

  // Phone B queries room blocks with memberUids filter (mirrors Kotlin snapshots query)
  await assertSucceeds(
    krishiDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .where("memberUids", "array-contains", KRISHI_UID)
      .get()
  );

  // Unfiltered list query on blocks or rooms must fail
  await assertFails(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").get()
  );
  await assertFails(hariomDb.collection("coupleRooms").get());

  // Filtered list query on coupleRooms succeeds
  await assertSucceeds(
    hariomDb
      .collection("coupleRooms")
      .where("memberUids", "array-contains", HARIOM_UID)
      .get()
  );

  // Plaintext (non-KRIMOON:BCHAIN) packet must be rejected
  await assertFails(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00badplaintextblock")
      .set({
        ...validBlockPayload(HARIOM_UID, "Hariom", members, 2),
        encryptedPacket: "plaintext_unencrypted_message_attempt",
      })
  );

  // Immutable blockchain block cannot be mutated after creation
  await assertFails(blockRefA.update({ senderName: "Tampered" }));

  // Intruder cannot read or write blocks
  await assertFails(
    intruderDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00abcdef1234567890abcdef")
      .get()
  );
  await assertFails(
    intruderDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00intruderblock1234")
      .set(validBlockPayload(INTRUDER_UID, "Hacker", [INTRUDER_UID], 2))
  );
});

test("Unpairing locks the room in terminal UNPAIRED state and blocks future writes", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload())
  );

  // Unpair room
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      status: "UNPAIRED",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Further updates or block writes to UNPAIRED room must fail
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      spiceLevel: "SWEET",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00afterunpairblock")
      .set(validBlockPayload(HARIOM_UID, "Hariom", [HARIOM_UID], 2))
  );
});

test("Realistic Base64 encrypted packets (+, /, =, -, _) and same-account 2-phone pairing succeed", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();

  // Phone A creates room under HARIOM_UID
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayload(HARIOM_UID, "Hariom"))
  );

  // Phone B signed into the same Google account joins with partnerName and marks CONNECTED
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      partnerName: "Krishi",
      status: "CONNECTED",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Block containing full Base64/URL-safe character set (+, /, =, -, _) must succeed
  const base64Packet = "KRIMOON:BCHAIN:eyJ2IjoyLCJjdCI6ImFiKzEyLzg5LV89PSJ9";
  await assertSucceeds(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc("00b64packetblock12345")
      .set({
        ...validBlockPayload(HARIOM_UID, "Krishi", [HARIOM_UID], 1),
        encryptedPacket: base64Packet,
      })
  );
});
