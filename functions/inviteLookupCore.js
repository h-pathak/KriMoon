"use strict";

const COUPLE_CODE_REGEX = /^[0-9]{6}$/;
const ALLOWED_STATUSES = new Set(["WAITING", "PENDING", "LOCKED", "RETIRED"]);

const DEFAULT_CONFIG = Object.freeze({
  maxPerMinute: 10,
  minuteWindowMs: 60 * 1000,
  maxPerDay: 30,
  dayWindowMs: 24 * 60 * 60 * 1000,
  missLockoutThreshold: 5,
  baseMissLockoutMs: 30 * 1000,
  maxMissLockoutMs: 15 * 60 * 1000,
  maxDisplayHintLength: 40,
});

class CallableError extends Error {
  constructor(code, message, details = undefined) {
    super(message);
    this.name = "CallableError";
    this.code = code;
    this.details = details;
  }
}

/**
 * Pure, dependency-injected implementation of the rate-limited Couple Lounge
 * invitation lookup callable.
 *
 * Security invariants:
 * - Requires an authenticated Firebase UID.
 * - Strictly validates 6-digit numeric couple codes before touching Firestore.
 * - Enforces per-UID sliding-window (per-minute), daily quota, and consecutive-miss
 *   exponential backoff lockouts inside a single Firestore transaction on the
 *   server-only `_rateLimits` collection.
 * - Returns only minimal invite availability fields; never returns UIDs, ECDH
 *   public keys, hash chains, or room contents.
 */
async function lookupCoupleInviteCore({
  authUid,
  rawCoupleCode,
  db,
  nowMs = Date.now(),
  config = DEFAULT_CONFIG,
}) {
  const cfg = { ...DEFAULT_CONFIG, ...config };

  if (typeof authUid !== "string" || authUid.trim().length === 0) {
    throw new CallableError(
      "unauthenticated",
      "Authentication is required to look up a Couple Lounge code."
    );
  }

  const coupleCode =
    typeof rawCoupleCode === "string" ? rawCoupleCode.trim() : "";
  if (!COUPLE_CODE_REGEX.test(coupleCode)) {
    throw new CallableError(
      "invalid-argument",
      "Couple code must be exactly 6 numeric digits."
    );
  }

  const rateDocId = `inviteLookup_${authUid}`;
  const rateRef = db.collection("_rateLimits").doc(rateDocId);
  const inviteRef = db.collection("coupleInvites").doc(coupleCode);

  return db.runTransaction(async (tx) => {
    // Firestore requires all reads before any writes within a transaction.
    const rateSnap = await tx.get(rateRef);
    const inviteSnap = await tx.get(inviteRef);

    const prev = rateSnap.exists && rateSnap.data() ? rateSnap.data() : {};

    // 1. Check active enumeration-lockout cooldown.
    const lockedUntilMs =
      typeof prev.lockedUntilMs === "number" ? prev.lockedUntilMs : 0;
    if (lockedUntilMs > nowMs) {
      const retryAfterSeconds = Math.max(
        1,
        Math.ceil((lockedUntilMs - nowMs) / 1000)
      );
      throw new CallableError(
        "resource-exhausted",
        "Too many unsuccessful code lookups. Please wait before trying again.",
        { reason: "miss_lockout", retryAfterSeconds }
      );
    }

    // 2. Check per-minute sliding window.
    const rawRecent = Array.isArray(prev.recentTimestamps)
      ? prev.recentTimestamps
      : [];
    const recentTimestamps = rawRecent.filter(
      (ts) => typeof ts === "number" && nowMs - ts < cfg.minuteWindowMs
    );
    if (recentTimestamps.length >= cfg.maxPerMinute) {
      const oldestInWindow = Math.min(...recentTimestamps);
      const retryAfterSeconds = Math.max(
        1,
        Math.ceil((oldestInWindow + cfg.minuteWindowMs - nowMs) / 1000)
      );
      throw new CallableError(
        "resource-exhausted",
        "Rate limit exceeded. Please wait a moment before trying another code.",
        { reason: "per_minute_limit", retryAfterSeconds }
      );
    }

    // 3. Check 24-hour rolling quota.
    const prevDayWindowStartMs =
      typeof prev.dayWindowStartMs === "number" ? prev.dayWindowStartMs : nowMs;
    const isSameDayWindow = nowMs - prevDayWindowStartMs < cfg.dayWindowMs;
    const dayWindowStartMs = isSameDayWindow ? prevDayWindowStartMs : nowMs;
    const prevDayCount =
      isSameDayWindow && typeof prev.dayCount === "number" ? prev.dayCount : 0;

    if (prevDayCount >= cfg.maxPerDay) {
      const retryAfterSeconds = Math.max(
        1,
        Math.ceil((dayWindowStartMs + cfg.dayWindowMs - nowMs) / 1000)
      );
      throw new CallableError(
        "resource-exhausted",
        "Daily code lookup limit reached. Please try again tomorrow.",
        { reason: "daily_limit", retryAfterSeconds }
      );
    }

    // 4. Inspect minimal invite index document.
    const inviteData =
      inviteSnap.exists && inviteSnap.data() ? inviteSnap.data() : null;
    const exists = inviteData !== null;
    const rawStatus =
      exists && typeof inviteData.status === "string"
        ? inviteData.status
        : "NOT_FOUND";
    const status = exists
      ? ALLOWED_STATUSES.has(rawStatus)
        ? rawStatus
        : "LOCKED"
      : "NOT_FOUND";

    // Treat missing or retired codes as enumeration misses; reset streak on live WAITING invite.
    const isMiss = !exists || status === "RETIRED";
    const prevMissStreak =
      typeof prev.missStreak === "number" ? prev.missStreak : 0;
    const nextMissStreak = isMiss ? prevMissStreak + 1 : 0;

    let nextLockedUntilMs = 0;
    if (nextMissStreak >= cfg.missLockoutThreshold) {
      const exponent = nextMissStreak - cfg.missLockoutThreshold;
      const lockoutDurationMs = Math.min(
        cfg.maxMissLockoutMs,
        cfg.baseMissLockoutMs * Math.pow(2, exponent)
      );
      nextLockedUntilMs = nowMs + lockoutDurationMs;
    }

    // 5. Persist updated per-UID rate-limit state atomically.
    tx.set(rateRef, {
      uid: authUid,
      recentTimestamps: [...recentTimestamps, nowMs],
      dayWindowStartMs,
      dayCount: prevDayCount + 1,
      missStreak: nextMissStreak,
      lockedUntilMs: nextLockedUntilMs,
      updatedAtMs: nowMs,
    });

    // 6. Return strictly minimal response payload.
    const rawHint =
      exists &&
      status === "WAITING" &&
      typeof inviteData.hostDisplayHint === "string"
        ? inviteData.hostDisplayHint.trim().slice(0, cfg.maxDisplayHintLength)
        : "";

    const schemaVersion =
      exists && typeof inviteData.schemaVersion === "number"
        ? inviteData.schemaVersion
        : 0;

    return {
      exists,
      coupleCode,
      status,
      hostDisplayHint: rawHint,
      schemaVersion,
    };
  });
}

module.exports = {
  CallableError,
  DEFAULT_CONFIG,
  lookupCoupleInviteCore,
};
