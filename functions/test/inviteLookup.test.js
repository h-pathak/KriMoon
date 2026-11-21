"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CallableError,
  lookupCoupleInviteCore,
} = require("../inviteLookupCore");

/**
 * Deterministic in-memory Firestore stub enforcing Firestore's read-before-write
 * transaction rule so unit tests run offline with zero external dependencies.
 */
function createMockFirestore(initialCollections = {}) {
  const store = new Map();
  for (const [colName, docs] of Object.entries(initialCollections)) {
    for (const [docId, value] of Object.entries(docs)) {
      store.set(`${colName}/${docId}`, structuredClone(value));
    }
  }

  return {
    collection(colName) {
      return {
        doc(docId) {
          return { path: `${colName}/${docId}` };
        },
      };
    },
    async runTransaction(fn) {
      let hasWritten = false;
      const stagedWrites = new Map();

      const tx = {
        async get(docRef) {
          if (hasWritten) {
            throw new Error(
              "Firestore transactions require all reads to be executed before all writes."
            );
          }
          const exists = store.has(docRef.path);
          return {
            exists,
            data: () => (exists ? structuredClone(store.get(docRef.path)) : undefined),
          };
        },
        set(docRef, value) {
          hasWritten = true;
          stagedWrites.set(docRef.path, structuredClone(value));
        },
      };

      const result = await fn(tx);
      for (const [path, value] of stagedWrites.entries()) {
        store.set(path, value);
      }
      return result;
    },
    _dump(path) {
      return store.get(path);
    },
  };
}

test("rejects unauthenticated callers", async () => {
  const db = createMockFirestore();
  await assert.rejects(
    () =>
      lookupCoupleInviteCore({
        authUid: "",
        rawCoupleCode: "123456",
        db,
        nowMs: 1_000_000,
      }),
    (err) => err instanceof CallableError && err.code === "unauthenticated"
  );
});

test("rejects malformed or non-6-digit couple codes without consuming quota", async () => {
  const db = createMockFirestore();
  for (const badCode of ["12345", "1234567", "12ab56", "", null, 123456]) {
    await assert.rejects(
      () =>
        lookupCoupleInviteCore({
          authUid: "user-a",
          rawCoupleCode: badCode,
          db,
          nowMs: 1_000_000,
        }),
      (err) => err instanceof CallableError && err.code === "invalid-argument"
    );
  }
  assert.equal(db._dump("_rateLimits/inviteLookup_user-a"), undefined);
});

test("returns strictly minimal fields for WAITING invite and strips sensitive fields", async () => {
  const db = createMockFirestore({
    coupleInvites: {
      "482910": {
        coupleCode: "482910",
        status: "WAITING",
        hostDisplayHint: "Aarav",
        schemaVersion: 2,
        // Extra sensitive fields that must NEVER be returned even if present in storage
        hostUid: "secret-host-uid",
        hostPublicKey: "BASE64_ECDH_KEY",
        latestBlockHash: "deadbeef",
      },
    },
  });

  const res = await lookupCoupleInviteCore({
    authUid: "guest-1",
    rawCoupleCode: " 482910 ",
    db,
    nowMs: 1_000_000,
  });

  assert.deepEqual(res, {
    exists: true,
    coupleCode: "482910",
    status: "WAITING",
    hostDisplayHint: "Aarav",
    schemaVersion: 2,
  });
  assert.equal("hostUid" in res, false);
  assert.equal("hostPublicKey" in res, false);
  assert.equal("latestBlockHash" in res, false);
});

test("omits hostDisplayHint for LOCKED, PENDING, and RETIRED invites", async () => {
  const db = createMockFirestore({
    coupleInvites: {
      "111111": { status: "LOCKED", hostDisplayHint: "HiddenName", schemaVersion: 2 },
      "222222": { status: "PENDING", hostDisplayHint: "HiddenName", schemaVersion: 2 },
      "333333": { status: "RETIRED", hostDisplayHint: "HiddenName", schemaVersion: 2 },
    },
  });

  const r1 = await lookupCoupleInviteCore({
    authUid: "guest-2",
    rawCoupleCode: "111111",
    db,
    nowMs: 1_000_000,
  });
  const r2 = await lookupCoupleInviteCore({
    authUid: "guest-2",
    rawCoupleCode: "222222",
    db,
    nowMs: 1_001_000,
  });
  const r3 = await lookupCoupleInviteCore({
    authUid: "guest-2",
    rawCoupleCode: "333333",
    db,
    nowMs: 1_002_000,
  });

  assert.equal(r1.status, "LOCKED");
  assert.equal(r1.hostDisplayHint, "");
  assert.equal(r2.status, "PENDING");
  assert.equal(r2.hostDisplayHint, "");
  assert.equal(r3.status, "RETIRED");
  assert.equal(r3.hostDisplayHint, "");
});

test("enforces per-minute sliding-window rate limit (blocks 11th request within 60s)", async () => {
  const db = createMockFirestore({
    coupleInvites: {
      "654321": { status: "WAITING", hostDisplayHint: "Partner", schemaVersion: 2 },
    },
  });

  const start = 5_000_000;
  for (let i = 0; i < 10; i++) {
    const res = await lookupCoupleInviteCore({
      authUid: "user-rate",
      rawCoupleCode: "654321",
      db,
      nowMs: start + i * 1_000,
    });
    assert.equal(res.exists, true);
  }

  // 11th request at +10s must fail with per_minute_limit
  await assert.rejects(
    () =>
      lookupCoupleInviteCore({
        authUid: "user-rate",
        rawCoupleCode: "654321",
        db,
        nowMs: start + 10_000,
      }),
    (err) =>
      err instanceof CallableError &&
      err.code === "resource-exhausted" &&
      err.details?.reason === "per_minute_limit" &&
      err.details?.retryAfterSeconds >= 49
  );

  // After the 60s window slides past the first timestamp, request succeeds again
  const recovered = await lookupCoupleInviteCore({
    authUid: "user-rate",
    rawCoupleCode: "654321",
    db,
    nowMs: start + 61_000,
  });
  assert.equal(recovered.exists, true);
});

test("triggers exponential cooldown lockout after 5 consecutive NOT_FOUND misses", async () => {
  const db = createMockFirestore();
  const base = 10_000_000;

  for (let i = 0; i < 5; i++) {
    const res = await lookupCoupleInviteCore({
      authUid: "enumerator-1",
      rawCoupleCode: String(100000 + i),
      db,
      nowMs: base + i * 1_000,
    });
    assert.equal(res.exists, false);
    assert.equal(res.status, "NOT_FOUND");
  }

  // 6th attempt immediately after is rejected by miss_lockout for 30s
  await assert.rejects(
    () =>
      lookupCoupleInviteCore({
        authUid: "enumerator-1",
        rawCoupleCode: "100005",
        db,
        nowMs: base + 5_000,
      }),
    (err) =>
      err instanceof CallableError &&
      err.code === "resource-exhausted" &&
      err.details?.reason === "miss_lockout" &&
      err.details?.retryAfterSeconds >= 25
  );

  // After 30s cooldown expires, a 6th miss extends lockout to 60s
  const sixthMiss = await lookupCoupleInviteCore({
    authUid: "enumerator-1",
    rawCoupleCode: "100005",
    db,
    nowMs: base + 35_000,
  });
  assert.equal(sixthMiss.exists, false);

  await assert.rejects(
    () =>
      lookupCoupleInviteCore({
        authUid: "enumerator-1",
        rawCoupleCode: "100006",
        db,
        nowMs: base + 36_000,
      }),
    (err) =>
      err instanceof CallableError &&
      err.code === "resource-exhausted" &&
      err.details?.reason === "miss_lockout" &&
      err.details?.retryAfterSeconds >= 55
  );
});

test("enforces 24-hour daily quota across windows", async () => {
  const db = createMockFirestore({
    coupleInvites: {
      "777777": { status: "WAITING", hostDisplayHint: "Maya", schemaVersion: 2 },
    },
  });

  const start = 20_000_000;
  // Spread 30 lookups across 3 separate 2-minute windows so per-minute limit is never hit
  for (let i = 0; i < 30; i++) {
    const res = await lookupCoupleInviteCore({
      authUid: "heavy-user",
      rawCoupleCode: "777777",
      db,
      nowMs: start + i * 10_000,
    });
    assert.equal(res.exists, true);
  }

  // 31st request in same 24h window must fail with daily_limit
  await assert.rejects(
    () =>
      lookupCoupleInviteCore({
        authUid: "heavy-user",
        rawCoupleCode: "777777",
        db,
        nowMs: start + 30 * 10_000 + 120_000,
      }),
    (err) =>
      err instanceof CallableError &&
      err.code === "resource-exhausted" &&
      err.details?.reason === "daily_limit"
  );
});

test("isolates rate limits per authenticated UID", async () => {
  const db = createMockFirestore({
    coupleInvites: {
      "888888": { status: "WAITING", hostDisplayHint: "Sam", schemaVersion: 2 },
    },
  });

  const start = 30_000_000;
  for (let i = 0; i < 5; i++) {
    await lookupCoupleInviteCore({
      authUid: "uid-blocked",
      rawCoupleCode: String(900000 + i),
      db,
      nowMs: start + i * 1_000,
    });
  }

  // uid-blocked is locked out, but uid-clean can still look up legitimately
  const cleanResult = await lookupCoupleInviteCore({
    authUid: "uid-clean",
    rawCoupleCode: "888888",
    db,
    nowMs: start + 6_000,
  });
  assert.equal(cleanResult.exists, true);
  assert.equal(cleanResult.hostDisplayHint, "Sam");
});
