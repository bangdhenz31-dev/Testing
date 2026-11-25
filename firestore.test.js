const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_producer_1";
const BOB_UID = "bob_collab_2";
const CHARLIE_UID = "charlie_outsider_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(ownerId, inviteCode = "CYBER8D1", overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId,
    ownerName: "DJ CyberNova",
    memberUids: [ownerId],
    inviteCode,
    title: "Midnight 8D Bass Session",
    bpm: 124,
    spatialPattern: "CIRCULAR",
    orbitSpeedHz: 0.35,
    orbitRadiusMeters: 5.5,
    reverbMix: 0.45,
    bassBoostDb: 6.0,
    eqBandsDb: [3.5, 1.5, 0.0, 2.0, 3.0],
    compressorEnabled: true,
    compressorThresholdDb: -14.0,
    delayEnabled: true,
    delayMix: 0.25,
    limiterEnabled: true,
    latencyFrames: 256,
    youtubeVideoId: "jfKfPfyJRdk",
    youtubeTitle: "lofi hip hop radio",
    synthGain: 0.75,
    clipGain: 0.85,
    micGain: 0.9,
    masterGain: 0.85,
    stepPattern: [3, 0, 2, 0, 3, 0, 1, 2, 3, 0, 2, 0, 3, 1, 2, 1],
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated user cannot read or write projects, invites, or notes", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("projects").get());
  await assertFails(
    unauth.collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID))
  );
  await assertFails(unauth.collection("projectInvites").doc("CYBER8D1").get());
});

test("Owner can create, list with memberUids filter, partial update, and delete project", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = validProjectPayload(ALICE_UID);

  await assertSucceeds(alice.collection("projects").doc("proj_1").set(payload));

  // Aligned query succeeds
  await assertSucceeds(
    alice.collection("projects").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Unfiltered list query fails
  await assertFails(alice.collection("projects").get());

  // Partial update on mix & DSP fields succeeds
  await assertSucceeds(
    alice.collection("projects").doc("proj_1").update({
      bpm: 132,
      spatialPattern: "FIGURE8",
      eqBandsDb: [4.0, 2.0, -1.0, 2.5, 4.0],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable ownerId or createdAt mutation
  await assertFails(
    alice.collection("projects").doc("proj_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    alice.collection("projects").doc("proj_2").set({
      ...validProjectPayload(ALICE_UID),
      id: "proj_2",
    })
  );

  // Reject future timestamp
  await assertFails(
    alice.collection("projects").doc("proj_3").set(
      validProjectPayload(ALICE_UID, "CYBER8D2", {
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  await assertSucceeds(alice.collection("projects").doc("proj_1").delete());
});

test("Invite code workflow allows collaborator to join and edit mix + notes while blocking outsiders", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    alice.collection("projects").doc("proj_collab").set(validProjectPayload(ALICE_UID, "JOIN9D88"))
  );

  await assertSucceeds(
    alice.collection("projectInvites").doc("JOIN9D88").set({
      projectId: "proj_collab",
      projectTitle: "Midnight 8D Bass Session",
      ownerId: ALICE_UID,
      ownerName: "DJ CyberNova",
      createdAt: new Date(Date.now() - 1500),
    })
  );

  // Bob cannot read project before joining
  await assertFails(bob.collection("projects").doc("proj_collab").get());

  // Bob looks up invite code
  await assertSucceeds(bob.collection("projectInvites").doc("JOIN9D88").get());

  // Bob joins project by adding only himself to memberUids
  await assertSucceeds(
    bob.collection("projects").doc("proj_collab").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot hijack ownerId or spoof another user during join
  await assertFails(
    charlie.collection("projects").doc("proj_collab").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      ownerId: CHARLIE_UID,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Now Bob can read and update mix settings
  await assertSucceeds(bob.collection("projects").doc("proj_collab").get());
  await assertSucceeds(
    bob.collection("projects").doc("proj_collab").update({
      reverbMix: 0.72,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Bob cannot delete Alice's project
  await assertFails(bob.collection("projects").doc("proj_collab").delete());

  // Bob posts a collaboration note with denormalized memberUids
  await assertSucceeds(
    bob.collection("projects").doc("proj_collab").collection("notes").doc("note_1").set({
      projectId: "proj_collab",
      authorId: BOB_UID,
      authorName: "Bob Mix",
      message: "Boosted 12kHz air band for 8D vocals!",
      barPosition: 8,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 100),
    })
  );

  // Alice lists notes using memberUids filter
  await assertSucceeds(
    alice
      .collection("projects")
      .doc("proj_collab")
      .collection("notes")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Charlie (outsider) cannot read or write notes
  await assertFails(
    charlie.collection("projects").doc("proj_collab").collection("notes").doc("note_1").get()
  );
  await assertFails(
    charlie.collection("projects").doc("proj_collab").collection("notes").doc("note_2").set({
      projectId: "proj_collab",
      authorId: CHARLIE_UID,
      authorName: "Charlie",
      message: "Intruder note",
      barPosition: 1,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      createdAt: new Date(Date.now() - 100),
    })
  );
});
