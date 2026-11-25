const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const ADMIN_UID = "admin_owner_01";
const ADMIN_EMAIL = "emonborah81@gmail.com";
const CAPTAIN_UID = "captain_01";
const VICE_CAPTAIN_UID = "vice_captain_01";
const VOLUNTEER_UID = "volunteer_01";
const STUDENT_1_UID = "student_01";
const STUDENT_2_UID = "student_02";

const pastDate = (offsetMs = 5000) => new Date(Date.now() - offsetMs);

async function seedBaseState() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = pastDate(10000);
    await db.collection("settings").doc("portal").set({
      registrationOpen: true,
      registrationScope: "2026",
      maxSportsEvents: 3,
      maxCulturalEvents: 3,
      closedMessage: "Registrations are closed.",
      updatedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("events").doc("sports_100m_race").set({
      name: "100m Race",
      normalizedName: "100m race",
      category: "SPORTS",
      enabled: true,
      archived: false,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("events").doc("sports_disabled_event").set({
      name: "Hidden Race",
      normalizedName: "hidden race",
      category: "SPORTS",
      enabled: false,
      archived: false,
      sortOrder: 2,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("events").doc("cultural_dance").set({
      name: "Dance",
      normalizedName: "dance",
      category: "CULTURAL",
      enabled: true,
      archived: false,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("management_profiles").doc(CAPTAIN_UID).set({
      userId: CAPTAIN_UID,
      email: "captain@school.edu",
      displayName: "House Captain",
      role: "CAPTAIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("management_profiles").doc(VICE_CAPTAIN_UID).set({
      userId: VICE_CAPTAIN_UID,
      email: "vice@school.edu",
      displayName: "Vice Captain",
      role: "VICE_CAPTAIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("management_profiles").doc(VOLUNTEER_UID).set({
      userId: VOLUNTEER_UID,
      email: "volunteer@school.edu",
      displayName: "House Volunteer",
      role: "VOLUNTEER",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });
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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
    await seedBaseState();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("settings").doc("portal").get());
  await assertFails(
    unauth.collection("events").where("enabled", "==", true).where("archived", "==", false).get()
  );
  await assertFails(unauth.collection("participants").get());
  await assertFails(unauth.collection("audit_logs").get());
  await assertFails(unauth.collection("management_invites").get());
});

test("Students can list active events only, and cannot read disabled events or other participants", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_1_UID, {
    email: "student1@school.edu",
    email_verified: true,
  }).firestore();

  // Exact Kotlin student query succeeds
  await assertSucceeds(
    studentDb
      .collection("events")
      .where("enabled", "==", true)
      .where("archived", "==", false)
      .get()
  );

  // Unfiltered event list fails for student
  await assertFails(studentDb.collection("events").get());

  // Disabled event direct get fails for student
  await assertFails(studentDb.collection("events").doc("sports_disabled_event").get());

  // Students cannot list participants or read audit logs or roll locks
  await assertFails(studentDb.collection("participants").get());
  await assertFails(studentDb.collection("roll_locks").get());
  await assertFails(studentDb.collection("audit_logs").get());
});

test("Student atomic registration creates participant, roll_lock, and selections; duplicate roll number fails", async () => {
  const student1 = testEnv.authenticatedContext(STUDENT_1_UID, {
    email: "student1@school.edu",
    email_verified: true,
  }).firestore();
  const student2 = testEnv.authenticatedContext(STUDENT_2_UID, {
    email: "student2@school.edu",
    email_verified: true,
  }).firestore();

  const ts = pastDate(2000);
  const batch1 = student1.batch();
  batch1.set(student1.collection("participants").doc("part_001"), {
    studentName: "Aarav Sharma",
    rollNumber: "10A-12",
    normalizedRollNumber: "10A-12",
    registrationScope: "2026",
    sportsEventIds: ["sports_100m_race"],
    culturalEventIds: ["cultural_dance"],
    sportsEventNames: ["100m Race"],
    culturalEventNames: ["Dance"],
    submittedByUid: STUDENT_1_UID,
    submittedAt: ts,
    createdAt: ts,
    updatedAt: ts,
  });
  batch1.set(student1.collection("roll_locks").doc("2026__10A-12"), {
    participantId: "part_001",
    registrationScope: "2026",
    normalizedRollNumber: "10A-12",
    submittedByUid: STUDENT_1_UID,
    createdAt: ts,
  });
  batch1.set(student1.collection("participant_event_selections").doc("part_001__sports_100m_race"), {
    participantId: "part_001",
    eventId: "sports_100m_race",
    eventName: "100m Race",
    category: "SPORTS",
    registrationScope: "2026",
    submittedByUid: STUDENT_1_UID,
    createdAt: ts,
  });
  await assertSucceeds(batch1.commit());

  // Student 2 cannot read Student 1's participant record
  await assertFails(student2.collection("participants").doc("part_001").get());

  // Duplicate roll number fails atomically because roll_locks doc already exists
  const batchDup = student2.batch();
  batchDup.set(student2.collection("participants").doc("part_002"), {
    studentName: "Duplicate Student",
    rollNumber: "10A-12",
    normalizedRollNumber: "10A-12",
    registrationScope: "2026",
    sportsEventIds: ["sports_100m_race"],
    culturalEventIds: [],
    sportsEventNames: ["100m Race"],
    culturalEventNames: [],
    submittedByUid: STUDENT_2_UID,
    submittedAt: ts,
    createdAt: ts,
    updatedAt: ts,
  });
  batchDup.set(student2.collection("roll_locks").doc("2026__10A-12"), {
    participantId: "part_002",
    registrationScope: "2026",
    normalizedRollNumber: "10A-12",
    submittedByUid: STUDENT_2_UID,
    createdAt: ts,
  });
  await assertFails(batchDup.commit());
});

test("Closed registration blocks student submissions server-side", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("settings").doc("portal").update({
      registrationOpen: false,
      updatedAt: pastDate(1000),
    });
  });

  const student1 = testEnv.authenticatedContext(STUDENT_1_UID, {
    email: "student1@school.edu",
    email_verified: true,
  }).firestore();
  const ts = pastDate(500);
  const batch = student1.batch();
  batch.set(student1.collection("participants").doc("part_closed"), {
    studentName: "Late Student",
    rollNumber: "11B-05",
    normalizedRollNumber: "11B-05",
    registrationScope: "2026",
    sportsEventIds: ["sports_100m_race"],
    culturalEventIds: [],
    sportsEventNames: ["100m Race"],
    culturalEventNames: [],
    submittedByUid: STUDENT_1_UID,
    submittedAt: ts,
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(student1.collection("roll_locks").doc("2026__11B-05"), {
    participantId: "part_closed",
    registrationScope: "2026",
    normalizedRollNumber: "11B-05",
    submittedByUid: STUDENT_1_UID,
    createdAt: ts,
  });
  await assertFails(batch.commit());
});

test("RBAC enforcement: Captain vs Vice Captain vs Volunteer permissions", async () => {
  // Seed one participant
  const ts = pastDate(8000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("participants").doc("p_1").set({
      studentName: "Riya Das",
      rollNumber: "12C-08",
      normalizedRollNumber: "12C-08",
      registrationScope: "2026",
      sportsEventIds: ["sports_100m_race"],
      culturalEventIds: [],
      sportsEventNames: ["100m Race"],
      culturalEventNames: [],
      submittedByUid: STUDENT_1_UID,
      submittedAt: ts,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("roll_locks").doc("2026__12C-08").set({
      participantId: "p_1",
      registrationScope: "2026",
      normalizedRollNumber: "12C-08",
      submittedByUid: STUDENT_1_UID,
      createdAt: ts,
    });
  });

  const captainDb = testEnv.authenticatedContext(CAPTAIN_UID).firestore();
  const viceDb = testEnv.authenticatedContext(VICE_CAPTAIN_UID).firestore();
  const volunteerDb = testEnv.authenticatedContext(VOLUNTEER_UID).firestore();

  // Volunteer: can view/list participants, cannot edit/delete, cannot manage events, cannot view audit logs, cannot control settings
  await assertSucceeds(volunteerDb.collection("participants").get());
  await assertFails(
    volunteerDb.collection("participants").doc("p_1").update({
      studentName: "Edited by Volunteer",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(volunteerDb.collection("participants").doc("p_1").delete());
  await assertFails(volunteerDb.collection("audit_logs").get());
  await assertFails(
    volunteerDb.collection("settings").doc("portal").update({
      registrationOpen: false,
      updatedByUid: VOLUNTEER_UID,
      updatedAt: pastDate(1000),
    })
  );

  // Vice Captain: can edit participant and manage events, CANNOT delete participant, CANNOT view audit logs, CANNOT toggle registration
  await assertSucceeds(
    viceDb.collection("participants").doc("p_1").update({
      studentName: "Riya Das Updated",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    viceDb.collection("events").doc("sports_100m_race").update({
      enabled: false,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(viceDb.collection("participants").doc("p_1").delete());
  await assertFails(viceDb.collection("events").doc("sports_100m_race").delete());
  await assertFails(viceDb.collection("audit_logs").get());
  await assertFails(
    viceDb.collection("settings").doc("portal").update({
      registrationOpen: false,
      updatedByUid: VICE_CAPTAIN_UID,
      updatedAt: pastDate(1000),
    })
  );

  // Captain: full control including registration control, audit log creation/viewing, and participant deletion
  await assertSucceeds(
    captainDb.collection("settings").doc("portal").update({
      registrationOpen: false,
      updatedByUid: CAPTAIN_UID,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    captainDb.collection("audit_logs").doc("log_01").set({
      actorUid: CAPTAIN_UID,
      actorName: "House Captain",
      actorRole: "CAPTAIN",
      action: "REGISTRATION_CLOSED",
      recordType: "SETTINGS",
      recordId: "portal",
      details: "Closed registration for 2026",
      createdAt: pastDate(1000),
    })
  );
  await assertSucceeds(captainDb.collection("audit_logs").get());
  // Audit logs are immutable even for Captain
  await assertFails(captainDb.collection("audit_logs").doc("log_01").delete());
  await assertFails(
    captainDb.collection("audit_logs").doc("log_01").update({ details: "Tampered" })
  );
  await assertSucceeds(captainDb.collection("participants").doc("p_1").delete());
});

test("Red Team: Shadow update ghost fields, email spoofing, and role self-escalation are rejected", async () => {
  // Unverified email spoofing bootstrapped admin must fail
  const spoofAdmin = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofAdmin.collection("participants").get());
  await assertFails(spoofAdmin.collection("audit_logs").get());

  // Verified bootstrapped admin succeeds
  const realAdmin = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(realAdmin.collection("participants").get());

  // Student cannot self-assign CAPTAIN profile without a valid invite
  const studentDb = testEnv.authenticatedContext(STUDENT_1_UID, {
    email: "student1@school.edu",
    email_verified: true,
  }).firestore();
  const ts = pastDate(1000);
  await assertFails(
    studentDb.collection("management_profiles").doc(STUDENT_1_UID).set({
      userId: STUDENT_1_UID,
      email: "student1@school.edu",
      displayName: "Sneaky Student",
      role: "CAPTAIN",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Volunteer cannot escalate own role in management_profiles
  const volunteerDb = testEnv.authenticatedContext(VOLUNTEER_UID).firestore();
  await assertFails(
    volunteerDb.collection("management_profiles").doc(VOLUNTEER_UID).update({
      role: "CAPTAIN",
      updatedAt: pastDate(500),
    })
  );

  // Ghost field ("isVerified") on event creation must fail
  const captainDb = testEnv.authenticatedContext(CAPTAIN_UID).firestore();
  await assertFails(
    captainDb.collection("events").doc("ghost_evt").set({
      name: "Bad Event",
      normalizedName: "bad event",
      category: "SPORTS",
      enabled: true,
      archived: false,
      sortOrder: 99,
      isVerified: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Future timestamp tampered beyond request.time fails
  await assertFails(
    captainDb.collection("events").doc("future_evt").set({
      name: "Future Event",
      normalizedName: "future event",
      category: "SPORTS",
      enabled: true,
      archived: false,
      sortOrder: 99,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("One-time invite setup code atomic redemption grants exact assigned role only once", async () => {
  const captainDb = testEnv.authenticatedContext(CAPTAIN_UID).firestore();
  const ts = pastDate(3000);
  const inviteHash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

  // Captain creates single-use Vice Captain setup invite
  await assertSucceeds(
    captainDb.collection("management_invites").doc(inviteHash).set({
      role: "VICE_CAPTAIN",
      label: "Vice Captain Pass",
      targetEmail: "newvc@school.edu",
      redeemed: false,
      redeemedByUid: "",
      createdByUid: CAPTAIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Wrong email cannot read or redeem targeted invite
  const wrongUser = testEnv.authenticatedContext("wrong_uid", {
    email: "other@school.edu",
    email_verified: true,
  }).firestore();
  await assertFails(wrongUser.collection("management_invites").doc(inviteHash).get());

  // Target user atomically redeems invite -> gets VICE_CAPTAIN role
  const newVcUid = "new_vc_uid";
  const newVcDb = testEnv.authenticatedContext(newVcUid, {
    email: "newvc@school.edu",
    email_verified: true,
  }).firestore();

  // Trying to escalate to CAPTAIN during redemption fails!
  const escalateBatch = newVcDb.batch();
  escalateBatch.update(newVcDb.collection("management_invites").doc(inviteHash), {
    redeemed: true,
    redeemedByUid: newVcUid,
    updatedAt: pastDate(1000),
  });
  escalateBatch.set(newVcDb.collection("management_invite_claims").doc(newVcUid), {
    inviteId: inviteHash,
    role: "CAPTAIN",
    claimedAt: pastDate(1000),
  });
  escalateBatch.set(newVcDb.collection("management_profiles").doc(newVcUid), {
    userId: newVcUid,
    email: "newvc@school.edu",
    displayName: "New VC",
    role: "CAPTAIN",
    active: true,
    createdAt: pastDate(1000),
    updatedAt: pastDate(1000),
  });
  await assertFails(escalateBatch.commit());

  // Matching role VICE_CAPTAIN succeeds!
  const validBatch = newVcDb.batch();
  validBatch.update(newVcDb.collection("management_invites").doc(inviteHash), {
    redeemed: true,
    redeemedByUid: newVcUid,
    updatedAt: pastDate(1000),
  });
  validBatch.set(newVcDb.collection("management_invite_claims").doc(newVcUid), {
    inviteId: inviteHash,
    role: "VICE_CAPTAIN",
    claimedAt: pastDate(1000),
  });
  validBatch.set(newVcDb.collection("management_profiles").doc(newVcUid), {
    userId: newVcUid,
    email: "newvc@school.edu",
    displayName: "New VC",
    role: "VICE_CAPTAIN",
    active: true,
    createdAt: pastDate(1000),
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(validBatch.commit());

  // Re-using the same redeemed invite fails!
  const reuseUser = testEnv.authenticatedContext("reuse_uid", {
    email: "newvc@school.edu",
    email_verified: true,
  }).firestore();
  await assertFails(reuseUser.collection("management_invites").doc(inviteHash).get());
});
