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
const GENESIS_HASH = "009dc43882abcdef1234567890abcdef1234567890abcdef1234567890abcd00";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRoomPayloadV2(uid = HARIOM_UID, name = "Hariom", pubKey = HOST_PUB_KEY) {
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
    latestBlockHash: GENESIS_HASH,
    schemaVersion: 2,
    migrationAnchorHash: "",
    migrationAnchorIndex: 0,
    tombstoned: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validInvitePayload(status = "WAITING", hostHint = "Ha", schemaVersion = 2) {
  return {
    coupleCode: ROOM_CODE,
    status,
    hostDisplayHint: hostHint,
    schemaVersion,
    updatedAt: new Date(Date.now() - 2000),
  };
}

function validLegacyV1RoomPayload(uid = HARIOM_UID, name = "Hariom", pubKey = HOST_PUB_KEY) {
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
    latestBlockHash: GENESIS_HASH,
    createdAt: now,
    updatedAt: now,
  };
}

function validBlockPayloadV2(
  senderUid,
  senderName,
  memberUids,
  idx,
  previousHash,
  blockHash,
  actionType = "QUIZ",
  clientTurnId = "turn_uuid_0001"
) {
  return {
    coupleCode: ROOM_CODE,
    senderUid,
    senderName,
    memberUids,
    blockIndex: idx,
    actionType,
    previousHash,
    blockHash,
    schemaVersion: 2,
    clientTurnId,
    encryptedPacket: "KRIMOON:BCHAIN:eyJ2IjozLCJwdiI6MywidGFnIjoiYWJjMTIzIn0=",
    createdAt: new Date(Date.now() - 1000),
  };
}

async function createV2RoomWithInvite(db, uid = HARIOM_UID, name = "Hariom", pubKey = HOST_PUB_KEY) {
  const batch = db.batch();
  batch.set(db.collection("coupleRooms").doc(ROOM_CODE), validRoomPayloadV2(uid, name, pubKey));
  batch.set(db.collection("coupleInvites").doc(ROOM_CODE), validInvitePayload("WAITING", name.slice(0, 2), 2));
  return batch.commit();
}

async function pairTwoPartnersV2(hariomDb, krishiDb) {
  await assertSucceeds(createV2RoomWithInvite(hariomDb));

  // Partner 2 blind atomic join (updates room + invite without reading room doc first)
  const joinBatch = krishiDb.batch();
  joinBatch.update(krishiDb.collection("coupleRooms").doc(ROOM_CODE), {
    pendingPartnerUid: KRISHI_UID,
    pendingPartnerName: "Krishi",
    pendingPartnerPublicKey: PARTNER_PUB_KEY,
    status: "PENDING_APPROVAL",
    updatedAt: new Date(Date.now() - 800),
  });
  joinBatch.update(krishiDb.collection("coupleInvites").doc(ROOM_CODE), {
    status: "PENDING",
    updatedAt: new Date(Date.now() - 800),
  });
  await assertSucceeds(joinBatch.commit());

  // Host approves partner + locks invite atomically
  const approveBatch = hariomDb.batch();
  approveBatch.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    partnerUid: KRISHI_UID,
    partnerName: "Krishi",
    partnerPublicKey: PARTNER_PUB_KEY,
    memberUids: [HARIOM_UID, KRISHI_UID],
    pendingPartnerUid: "",
    pendingPartnerName: "",
    pendingPartnerPublicKey: "",
    status: "CONNECTED",
    updatedAt: new Date(Date.now() - 600),
  });
  approveBatch.update(hariomDb.collection("coupleInvites").doc(ROOM_CODE), {
    status: "LOCKED",
    updatedAt: new Date(Date.now() - 600),
  });
  await assertSucceeds(approveBatch.commit());
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

test("Unauthenticated users cannot read or write couple rooms, invites, or blocks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("coupleRooms").doc(ROOM_CODE).get());
  await assertFails(unauthDb.collection("coupleRooms").get());
  await assertFails(unauthDb.collection("coupleInvites").doc(ROOM_CODE).get());
  await assertFails(unauthDb.collection("coupleInvites").get());
  await assertFails(
    unauthDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayloadV2())
  );
});

test("Minimal /coupleInvites lookup hides WAITING /coupleRooms from strangers and prevents listing", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();

  // Creating V2 room WITHOUT matching /coupleInvites doc MUST fail
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validRoomPayloadV2())
  );

  // Creating /coupleInvites WITHOUT matching /coupleRooms doc MUST fail
  await assertFails(
    hariomDb.collection("coupleInvites").doc(ROOM_CODE).set(validInvitePayload())
  );

  // Atomic creation of room + minimal invite succeeds
  await assertSucceeds(createV2RoomWithInvite(hariomDb));

  // Stranger CAN look up minimal /coupleInvites/{code} (exact get only)
  await assertSucceeds(krishiDb.collection("coupleInvites").doc(ROOM_CODE).get());
  // Stranger CANNOT list /coupleInvites
  await assertFails(krishiDb.collection("coupleInvites").get());

  // CRITICAL PRIVACY FIX: Stranger CANNOT read the WAITING /coupleRooms/{code} document!
  await assertFails(krishiDb.collection("coupleRooms").doc(ROOM_CODE).get());
});

test("Zero-Knowledge ECDH Handshake & Host Approval: rejects self-join, MITM key swap, and self-approval", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  await assertSucceeds(createV2RoomWithInvite(hariomDb));

  // Self-join loophole MUST be rejected (creator cannot join their own room as pendingPartner)
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      pendingPartnerUid: HARIOM_UID,
      pendingPartnerName: "Self",
      pendingPartnerPublicKey: PARTNER_PUB_KEY,
      status: "PENDING_APPROVAL",
      updatedAt: new Date(Date.now() - 800),
    })
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
      updatedAt: new Date(Date.now() - 740),
    })
  );

  // Partner 2 CANNOT overwrite hostDisplayHint or downgrade schemaVersion on /coupleInvites during join
  const tamperedInviteJoin = krishiDb.batch();
  tamperedInviteJoin.update(krishiDb.collection("coupleRooms").doc(ROOM_CODE), {
    pendingPartnerUid: KRISHI_UID,
    pendingPartnerName: "Krishi",
    pendingPartnerPublicKey: PARTNER_PUB_KEY,
    status: "PENDING_APPROVAL",
    updatedAt: new Date(Date.now() - 710),
  });
  tamperedInviteJoin.update(krishiDb.collection("coupleInvites").doc(ROOM_CODE), {
    status: "PENDING",
    hostDisplayHint: "XX",
    updatedAt: new Date(Date.now() - 710),
  });
  await assertFails(tamperedInviteJoin.commit());

  // Partner 2 submits blind atomic join request (room + invite) -> PENDING_APPROVAL
  const joinBatch = krishiDb.batch();
  joinBatch.update(krishiDb.collection("coupleRooms").doc(ROOM_CODE), {
    pendingPartnerUid: KRISHI_UID,
    pendingPartnerName: "Krishi",
    pendingPartnerPublicKey: PARTNER_PUB_KEY,
    status: "PENDING_APPROVAL",
    updatedAt: new Date(Date.now() - 700),
  });
  joinBatch.update(krishiDb.collection("coupleInvites").doc(ROOM_CODE), {
    status: "PENDING",
    updatedAt: new Date(Date.now() - 700),
  });
  await assertSucceeds(joinBatch.commit());

  // Now that Krishi IS pendingPartnerUid, she can observe her pending status
  await assertSucceeds(krishiDb.collection("coupleRooms").doc(ROOM_CODE).get());

  // Pending partner CANNOT self-approve
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

  // 3rd party intruder CANNOT read or hijack PENDING_APPROVAL room
  await assertFails(intruderDb.collection("coupleRooms").doc(ROOM_CODE).get());

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

  // Host approves Partner 2 atomically with invite lock
  const approveBatch = hariomDb.batch();
  approveBatch.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    partnerUid: KRISHI_UID,
    partnerName: "Krishi",
    partnerPublicKey: PARTNER_PUB_KEY,
    memberUids: [HARIOM_UID, KRISHI_UID],
    pendingPartnerUid: "",
    pendingPartnerName: "",
    pendingPartnerPublicKey: "",
    status: "CONNECTED",
    updatedAt: new Date(Date.now() - 500),
  });
  approveBatch.update(hariomDb.collection("coupleInvites").doc(ROOM_CODE), {
    status: "LOCKED",
    updatedAt: new Date(Date.now() - 500),
  });
  await assertSucceeds(approveBatch.commit());

  // Post-bonding: public keys are immutable
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
});

test("Bidirectional atomic block creation + head advancement: rejects orphan blocks, uncoupled head advances, and bad links", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();
  const members = [HARIOM_UID, KRISHI_UID];

  await pairTwoPartnersV2(hariomDb, krishiDb);

  const block1Hash = "00abcdef0123456789abcdef00000001";
  const block2Hash = "00abcdef0123456789abcdef00000002";

  // 1. Orphan block creation WITHOUT advancing room head MUST FAIL
  await assertFails(
    hariomDb
      .collection("coupleRooms")
      .doc(ROOM_CODE)
      .collection("blocks")
      .doc(block1Hash)
      .set(validBlockPayloadV2(HARIOM_UID, "Hariom", members, 1, GENESIS_HASH, block1Hash, "QUIZ", "turn-orphan-1"))
  );

  // 2. Room head advancement WITHOUT creating matching block MUST FAIL
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      blockCount: 2,
      latestBlockHash: block1Hash,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // 3. Mismatched previousHash MUST FAIL even in atomic batch
  const badPrevBatch = hariomDb.batch();
  badPrevBatch.set(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(block1Hash),
    validBlockPayloadV2(HARIOM_UID, "Hariom", members, 1, "00deadbeef1234567890abcdef123456", block1Hash, "QUIZ", "turn-badprev-1")
  );
  badPrevBatch.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    blockCount: 2,
    latestBlockHash: block1Hash,
    updatedAt: new Date(Date.now() - 350),
  });
  await assertFails(badPrevBatch.commit());

  // 4. Valid atomic block #1 + room head advancement SUCCEEDS
  const validBatch1 = hariomDb.batch();
  validBatch1.set(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(block1Hash),
    validBlockPayloadV2(HARIOM_UID, "Hariom", members, 1, GENESIS_HASH, block1Hash, "QUIZ", "turn-valid-001")
  );
  validBatch1.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    blockCount: 2,
    latestBlockHash: block1Hash,
    updatedAt: new Date(Date.now() - 300),
  });
  await assertSucceeds(validBatch1.commit());

  // 5. Valid atomic block #2 by Partner linked to block1Hash SUCCEEDS
  const validBatch2 = krishiDb.batch();
  validBatch2.set(
    krishiDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(block2Hash),
    validBlockPayloadV2(KRISHI_UID, "Krishi", members, 2, block1Hash, block2Hash, "WHISPER", "turn-valid-002")
  );
  validBatch2.update(krishiDb.collection("coupleRooms").doc(ROOM_CODE), {
    blockCount: 3,
    latestBlockHash: block2Hash,
    updatedAt: new Date(Date.now() - 200),
  });
  await assertSucceeds(validBatch2.commit());

  // 6. Immutable blocks cannot be updated or deleted, and intruder cannot read
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(block1Hash).update({ senderName: "Tampered" })
  );
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(block1Hash).delete()
  );
  await assertFails(
    intruderDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(block1Hash).get()
  );
});

test("Lazy V1->V2 migration atomically sets migrationAnchorHash & migrationAnchorIndex without rewriting V1 blocks", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();

  // Create legacy V1 room directly (backward-compatible schemaVersion=1)
  await assertSucceeds(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).set(validLegacyV1RoomPayload())
  );

  // Atomic V1 -> V2 upgrade with first V2 block anchored at roomBefore.latestBlockHash (GENESIS_HASH) and index 1
  const v2BlockHash = "00112233445566778899aabbccddeeff01";
  const migBatch = hariomDb.batch();
  migBatch.set(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(v2BlockHash),
    validBlockPayloadV2(HARIOM_UID, "Hariom", [HARIOM_UID], 1, GENESIS_HASH, v2BlockHash, "DARE", "turn-mig-001")
  );
  migBatch.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    blockCount: 2,
    latestBlockHash: v2BlockHash,
    schemaVersion: 2,
    migrationAnchorHash: GENESIS_HASH,
    migrationAnchorIndex: 1,
    updatedAt: new Date(Date.now() - 200),
  });
  await assertSucceeds(migBatch.commit());

  // Downgrading schemaVersion back to 1 MUST FAIL
  const downgradeHash = "00112233445566778899aabbccddeeff02";
  const badDowngrade = hariomDb.batch();
  badDowngrade.set(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).collection("blocks").doc(downgradeHash),
    validBlockPayloadV2(HARIOM_UID, "Hariom", [HARIOM_UID], 2, v2BlockHash, downgradeHash, "DARE", "turn-down-002")
  );
  badDowngrade.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    blockCount: 3,
    latestBlockHash: downgradeHash,
    schemaVersion: 1,
    updatedAt: new Date(Date.now() - 100),
  });
  await assertFails(badDowngrade.commit());
});

test("Account deletion tombstone scrubs PII/public keys, retires invite, and permanently reserves 6-digit code", async () => {
  const hariomDb = testEnv.authenticatedContext(HARIOM_UID).firestore();
  const krishiDb = testEnv.authenticatedContext(KRISHI_UID).firestore();
  const otherCoupleDb = testEnv.authenticatedContext(OTHER_COUPLE_UID).firestore();

  await pairTwoPartnersV2(hariomDb, krishiDb);

  // Direct room deletion remains forbidden
  await assertFails(hariomDb.collection("coupleRooms").doc(ROOM_CODE).delete());

  // Account deletion tombstone atomically scrubs names + public keys + retires invite
  const tombBatch = hariomDb.batch();
  tombBatch.update(hariomDb.collection("coupleRooms").doc(ROOM_CODE), {
    status: "UNPAIRED",
    tombstoned: true,
    creatorName: "",
    partnerName: "",
    creatorPublicKey: "",
    partnerPublicKey: "",
    pendingPartnerUid: "",
    pendingPartnerName: "",
    pendingPartnerPublicKey: "",
    updatedAt: new Date(Date.now() - 150),
  });
  tombBatch.update(hariomDb.collection("coupleInvites").doc(ROOM_CODE), {
    status: "RETIRED",
    hostDisplayHint: "",
    updatedAt: new Date(Date.now() - 150),
  });
  await assertSucceeds(tombBatch.commit());

  // Retired code can NEVER be reused by another couple
  await assertFails(createV2RoomWithInvite(otherCoupleDb, OTHER_COUPLE_UID, "Other"));
  await assertFails(otherCoupleDb.collection("coupleRooms").doc(ROOM_CODE).get());

  // Tombstoned room can NEVER be mutated again
  await assertFails(
    hariomDb.collection("coupleRooms").doc(ROOM_CODE).update({
      spiceLevel: "SWEET",
      updatedAt: new Date(Date.now() - 50),
    })
  );
});
