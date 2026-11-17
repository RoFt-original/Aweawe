const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

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

test("MemberProfile: valid create, update, and list succeed; invalid payload or immutable mutation fails", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const memberRef = db.collection("shared_members").doc("member_artem_1");

  await assertSucceeds(
    memberRef.set({
      memberId: "member_artem_1",
      nickname: "Артём",
      statusText: "На связи 🚀",
      avatarType: "PRESET",
      avatarData: "rocket",
      colorIndex: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(db.collection("shared_members").get());

  await assertSucceeds(
    memberRef.update({
      nickname: "Артём PRO",
      statusText: "Работаю над планом",
      avatarType: "PRESET",
      avatarData: "crown",
      colorIndex: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating createdAt or adding unexpected keys
  await assertFails(
    memberRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    db.collection("shared_members").doc("member_bad").set({
      id: "member_bad",
      memberId: "member_bad",
      nickname: "Bad",
      statusText: "",
      avatarType: "PRESET",
      avatarData: "star",
      colorIndex: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("SharedNote: valid create, co-editing update, list, and delete succeed; invalid schema fails", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const noteRef = db.collection("shared_notes").doc("note_1");

  await assertSucceeds(
    noteRef.set({
      title: "Общий список идей",
      content: "Все участники могут редактировать",
      category: "Идеи",
      colorIndex: 1,
      pinned: true,
      reminderEpochMillis: 0,
      authorId: "member_artem_1",
      authorName: "Артём",
      authorAvatarType: "PRESET",
      authorAvatarData: "rocket",
      authorColorIndex: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(db.collection("shared_notes").get());

  await assertSucceeds(
    noteRef.update({
      title: "Обновленный общий список",
      pinned: false,
      authorId: "member_anna_2",
      authorName: "Анна",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    noteRef.update({
      colorIndex: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("SharedScheduleItem: valid create, toggle completed, and list succeed; invalid dateKey/priority fail", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const itemRef = db.collection("shared_schedule").doc("sched_1");

  await assertSucceeds(
    itemRef.set({
      title: "Общий созвон команды",
      description: "Обсудить план на день",
      dateKey: "2026-11-17",
      startTime: "10:00",
      endTime: "11:00",
      priority: "HIGH",
      completed: false,
      reminderEnabled: true,
      reminderEpochMillis: 1794934800000,
      authorId: "member_artem_1",
      authorName: "Артём",
      authorAvatarType: "PRESET",
      authorAvatarData: "rocket",
      authorColorIndex: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(db.collection("shared_schedule").get());

  await assertSucceeds(
    itemRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    itemRef.update({
      priority: "INVALID",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
