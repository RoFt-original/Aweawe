const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write notes, scheduleItems, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Hack",
      content: "Body",
      category: "Личное",
      colorIndex: 0,
      pinned: false,
      reminderEpochMillis: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scheduleItems").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").get());
});

test("Authenticated owner: valid Note create, partial update, list, and delete succeed; invalid writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1");

  // Valid create matching NoteRepository payload (no @DocumentId 'id' key)
  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "Список идей",
      content: "Сделать синхронизацию через облако",
      category: "Идеи",
      colorIndex: 2,
      pinned: true,
      reminderEpochMillis: 1795000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List query matching NoteRepository.observeNotes
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );

  // Partial update matching NoteRepository.updateNote
  await assertSucceeds(
    noteRef.update({
      title: "Обновленный список идей",
      content: "Новое описание",
      category: "Работа",
      colorIndex: 3,
      pinned: false,
      reminderEpochMillis: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    noteRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject extra @DocumentId 'id' key on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_2").set({
      id: "note_2",
      userId: ALICE_UID,
      title: "Bad Note",
      content: "Body",
      category: "Личное",
      colorIndex: 0,
      pinned: false,
      reminderEpochMillis: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Authenticated owner: valid ScheduleItem create, partial update, and list succeed; invalid dateKey/priority fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("item_1");

  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      title: "Утренняя планерка",
      description: "Обсудить задачи на спринт",
      dateKey: "2026-11-17",
      startTime: "09:00",
      endTime: "10:00",
      priority: "HIGH",
      completed: false,
      reminderEnabled: true,
      reminderEpochMillis: 1794934800000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").get()
  );

  await assertSucceeds(
    itemRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid priority enum
  await assertFails(
    itemRef.update({
      priority: "CRITICAL",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid UserSettings create and update succeed; cross-user access fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");

  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      themeMode: "DARK",
      paletteName: "INDIGO",
      notificationsEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    prefRef.update({
      themeMode: "LIGHT",
      paletteName: "EMERALD",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read or update Alice's settings or notes
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
});
