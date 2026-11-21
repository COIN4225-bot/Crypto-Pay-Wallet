const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: denied across users, wallets, and transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("wallets").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("transactions").where("fromUid", "==", ALICE_UID).get()
  );
  await assertFails(
    unauthDb.collection("wallets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      balance: 10000,
      btc: 0,
      eth: 0,
      bnb: 0,
      demoCoin: 0,
      totalBalance: 10000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Users collection: owner create/update succeeds, cross-user edit and ghost fields fail, P2P lookup succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Crypto",
      email: "alice@example.com",
      walletAddress: "0xA11CE00000000000000000000000000000000123",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query users for P2P transfer lookup
  await assertSucceeds(
    bobDb.collection("users").where("email", "==", "alice@example.com").get()
  );
  await assertSucceeds(
    bobDb
      .collection("users")
      .where("walletAddress", "==", "0xA11CE00000000000000000000000000000000123")
      .get()
  );

  // Bob cannot modify Alice's profile
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      name: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice cannot mutate createdAt or add ghost fields
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Wallets collection: owner create/buy/sell succeeds, P2P credit-only update succeeds, P2P theft fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("wallets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      balance: 10000,
      btc: 0,
      eth: 0,
      bnb: 0,
      demoCoin: 0,
      totalBalance: 10000,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("wallets").doc(BOB_UID).set({
      userId: BOB_UID,
      balance: 10000,
      btc: 0,
      eth: 0,
      bnb: 0,
      demoCoin: 0,
      totalBalance: 10000,
      updatedAt: now,
    })
  );

  // Alice buys 0.05 BTC for 4500 USDT
  await assertSucceeds(
    aliceDb.collection("wallets").doc(ALICE_UID).update({
      balance: 5500,
      btc: 0.05,
      totalBalance: 10000,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Alice transfers 500 USDT to Bob (credit-only update on Bob's wallet)
  await assertSucceeds(
    aliceDb.collection("wallets").doc(BOB_UID).update({
      balance: 10500,
      totalBalance: 10500,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice CANNOT deduct from Bob's wallet (theft attempt blocked)
  await assertFails(
    aliceDb.collection("wallets").doc(BOB_UID).update({
      balance: 9000,
      totalBalance: 9000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unfiltered list on wallets fails
  await assertFails(aliceDb.collection("wallets").get());
  // Owner-scoped list succeeds
  await assertSucceeds(
    aliceDb.collection("wallets").where("userId", "==", ALICE_UID).get()
  );
});

test("Transactions collection: scoped queries succeed, unfiltered fails, immutable ledger", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Alice creates a transfer transaction to Bob
  await assertSucceeds(
    aliceDb.collection("transactions").doc("tx_1").set({
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      fromEmail: "alice@example.com",
      toEmail: "bob@example.com",
      amount: 500,
      usdtValue: 500,
      coin: "USDT",
      type: "transfer",
      status: "completed",
      timestamp: now,
      note: "Lunch split",
    })
  );

  // Alice cannot spoof a transaction from Bob
  await assertFails(
    aliceDb.collection("transactions").doc("tx_spoof").set({
      fromUid: BOB_UID,
      toUid: ALICE_UID,
      fromEmail: "bob@example.com",
      toEmail: "alice@example.com",
      amount: 500,
      usdtValue: 500,
      coin: "USDT",
      type: "transfer",
      status: "completed",
      timestamp: now,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("transactions").doc("tx_future").set({
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      fromEmail: "alice@example.com",
      toEmail: "bob@example.com",
      amount: 100,
      usdtValue: 100,
      coin: "USDT",
      type: "transfer",
      status: "completed",
      timestamp: new Date(Date.now() + 60000),
    })
  );

  // Alice & Bob can query their respective sides; Charlie cannot read tx_1
  await assertSucceeds(
    aliceDb.collection("transactions").where("fromUid", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("transactions").where("toUid", "==", BOB_UID).get()
  );
  await assertFails(charlieDb.collection("transactions").doc("tx_1").get());
  await assertFails(aliceDb.collection("transactions").get());

  // Transactions are immutable
  await assertFails(
    aliceDb.collection("transactions").doc("tx_1").update({ amount: 9999 })
  );
  await assertFails(aliceDb.collection("transactions").doc("tx_1").delete());
});

test("PriceAlerts & favoriteCoins: owner CRUD succeeds, cross-user access and invalid bounds fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Crypto",
      email: "alice@example.com",
      walletAddress: "0xA11CE00000000000000000000000000000000123",
      favoriteCoins: ["BTC", "ETH"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update favoriteCoins
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      favoriteCoins: ["BTC", "ETH", "BNB"],
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Create PriceAlert under users/{uid}/priceAlerts
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("priceAlerts")
      .doc("alert_1")
      .set({
        userId: ALICE_UID,
        coin: "BTC",
        direction: "ABOVE",
        targetPriceUsdt: 95000,
        active: true,
        triggeredCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  // List & update PriceAlert when triggered
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("priceAlerts").get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("priceAlerts")
      .doc("alert_1")
      .update({
        active: false,
        triggeredCount: 1,
        lastTriggeredAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Cross-user read/write/delete on Alice's priceAlerts must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("priceAlerts").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("priceAlerts")
      .doc("alert_1")
      .delete()
  );

  // Invalid direction or targetPriceUsdt <= 0 must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("priceAlerts")
      .doc("alert_bad")
      .set({
        userId: ALICE_UID,
        coin: "BTC",
        direction: "SIDEWAYS",
        targetPriceUsdt: -10,
        active: true,
        triggeredCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("priceAlerts")
      .doc("alert_1")
      .delete()
  );
});
