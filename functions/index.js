"use strict";

const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { CallableError, lookupCoupleInviteCore } = require("./inviteLookupCore");

/**
 * Target named Firestore database for KriMoon (verified in app/src/main/res/values/firebase_applet_config.xml
 * and firebase.json). Can be overridden via KRIMOON_FIRESTORE_DATABASE_ID environment variable.
 */
const NAMED_DATABASE_ID =
  process.env.KRIMOON_FIRESTORE_DATABASE_ID ||
  "ai-studio-android-krimoon-379cf576-7e8a-463d-afe3-d9a0cd5f3149";

function getAdminFirestore() {
  const app = getApps().length > 0 ? getApps()[0] : initializeApp();
  return getFirestore(app, NAMED_DATABASE_ID);
}

/**
 * Trusted Firebase Callable Cloud Function for Couple Lounge 6-digit invite lookup.
 *
 * Deployment checklist note:
 * - Deploy ONLY after explicit release approval:
 *     firebase deploy --only functions:lookupCoupleInvite --project gen-lang-client-0901950391
 * - `enforceAppCheck` starts as `false` during Play Integrity monitoring rollout.
 *   Flip to `true` ONLY after Firebase Console App Check metrics confirm verified
 *   Play Integrity traffic on signed release builds.
 */
exports.lookupCoupleInvite = onCall(
  {
    region: "us-central1",
    maxInstances: 20,
    timeoutSeconds: 15,
    memory: "256MiB",
    enforceAppCheck: false,
  },
  async (request) => {
    const authUid = request.auth ? request.auth.uid : null;
    const rawCoupleCode =
      request.data && typeof request.data === "object"
        ? request.data.coupleCode
        : null;

    try {
      const db = getAdminFirestore();
      return await lookupCoupleInviteCore({
        authUid,
        rawCoupleCode,
        db,
      });
    } catch (err) {
      if (err instanceof CallableError) {
        throw new HttpsError(err.code, err.message, err.details);
      }
      throw new HttpsError(
        "internal",
        "Unable to look up invite code at this time."
      );
    }
  }
);
