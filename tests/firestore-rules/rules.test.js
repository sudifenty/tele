// Firestore rules tests for Tele, run against the local emulator.
// Reads the rules from the repo root (firestore.rules). Run from the repo root:
//   firebase emulators:start --only firestore   (in one terminal)
//   cd tests/firestore-rules && npm install && npm test   (in another)
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, getDocs, setDoc, updateDoc, collection, Timestamp } = require('firebase/firestore');

const PROJECT = 'calculetor-29a74';
const P1 = 'parent-one';     // parent of family A
const P2 = 'parent-two';     // parent of family B
const C1 = 'child-anon-1';   // anonymous uid of the phone linked to family A (device dA)
const C2 = 'child-anon-2';   // anonymous uid of the phone linked to family B (device dB)
const CODE = '123456';

let env;
let results = [];

async function check(name, fn) {
  try {
    await fn();
    results.push(['PASS', name]);
  } catch (e) {
    results.push(['FAIL', name + ' :: ' + e.message.split('\n')[0]]);
  }
}

(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', '..', 'firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8080 },
  });

  // Start from empty data so the test can run again.
  await env.clearFirestore();

  // Seed two families with one parent each and one linked phone each.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const future = Timestamp.fromDate(new Date(Date.now() + 60 * 60 * 1000));
    await setDoc(doc(db, 'families/famA'), { name: 'Family A', parentUids: [P1] });
    await setDoc(doc(db, 'families/famB'), { name: 'Family B', parentUids: [P2] });
    await setDoc(doc(db, 'parents', P1), { familyId: 'famA' });
    await setDoc(doc(db, 'parents', P2), { familyId: 'famB' });
    await setDoc(doc(db, 'families/famA/devices/dA'), { ownerUid: C1, label: 'Phone A' });
    await setDoc(doc(db, 'families/famB/devices/dB'), { ownerUid: C2, label: 'Phone B' });
    await setDoc(doc(db, 'families/famA/devices/dA/location/current'), { lat: -0.35, lng: 32.58, ts: 1000 });
    await setDoc(doc(db, 'families/famB/devices/dB/location/current'), { lat: 0.31, lng: 32.58, ts: 1000 });
    await setDoc(doc(db, 'pairings', CODE), { familyId: 'famA', createdBy: P1, expiresAt: future });
  });

  const p1 = env.authenticatedContext(P1).firestore();
  const p2 = env.authenticatedContext(P2).firestore();
  const c1 = env.authenticatedContext(C1).firestore();
  const c2 = env.authenticatedContext(C2).firestore();
  const anon = env.unauthenticatedContext().firestore();

  // Parent reads: own family only
  await check('parent reads own family', () => assertSucceeds(getDoc(doc(p1, 'families/famA'))));
  await check('parent cannot read other family', () => assertFails(getDoc(doc(p1, 'families/famB'))));
  await check('parent lists own devices', () => assertSucceeds(getDocs(collection(p1, 'families/famA/devices'))));
  await check('parent cannot list other devices', () => assertFails(getDocs(collection(p1, 'families/famB/devices'))));

  // Parent reads live location: own family only
  await check('parent reads own child location', () =>
    assertSucceeds(getDoc(doc(p1, 'families/famA/devices/dA/location/current'))));
  await check('parent cannot read other family location', () =>
    assertFails(getDoc(doc(p1, 'families/famB/devices/dB/location/current'))));

  // Parent cannot write location, calls or SOS
  await check('parent cannot write child location', () =>
    assertFails(setDoc(doc(p1, 'families/famA/devices/dA/location/current'), { lat: 1, lng: 1, ts: 2 })));
  await check('parent cannot add call records', () =>
    assertFails(setDoc(doc(p1, 'families/famA/devices/dA/calls/c1'), { id: 1, number: '07', ts: 1, type: 'incoming' })));

  // Parent record points only at a family the parent belongs to
  await check('parent can point own record at own family', () =>
    assertSucceeds(setDoc(doc(p1, 'parents', P1), { familyId: 'famA' })));
  await check('parent cannot point record at another family', () =>
    assertFails(setDoc(doc(p1, 'parents', P1), { familyId: 'famB' })));
  await check("parent cannot write another parent's record", () =>
    assertFails(setDoc(doc(p1, 'parents', P2), { familyId: 'famA' })));

  // Child phone: writes only its own device
  await check('child writes own location', () =>
    assertSucceeds(setDoc(doc(c1, 'families/famA/devices/dA/location/current'), { lat: -0.36, lng: 32.59, ts: 3000 })));
  await check('child cannot write other device location', () =>
    assertFails(setDoc(doc(c1, 'families/famB/devices/dB/location/current'), { lat: 0, lng: 0, ts: 3000 })));
  await check('child rejects invalid latitude', () =>
    assertFails(setDoc(doc(c1, 'families/famA/devices/dA/location/current'), { lat: 120, lng: 0, ts: 3000 })));
  await check('child reads own device', () => assertSucceeds(getDoc(doc(c1, 'families/famA/devices/dA'))));
  await check('child cannot read other family', () => assertFails(getDoc(doc(c2, 'families/famA/devices/dA'))));
  await check('child cannot list family devices', () => assertFails(getDocs(collection(c1, 'families/famA/devices'))));
  await check('child cannot read parent record of another family', () => assertFails(getDoc(doc(c2, 'parents', P1))));

  // Anonymous (not signed in) users get nothing
  await check('signed-out user cannot read family', () => assertFails(getDoc(doc(anon, 'families/famA'))));
  await check('signed-out user cannot read location', () =>
    assertFails(getDoc(doc(anon, 'families/famA/devices/dA/location/current'))));

  // Pairing: a parent creates codes only for their own family
  await check('parent creates pairing for own family', () =>
    assertSucceeds(setDoc(doc(p1, 'pairings', '654321'), { familyId: 'famA', createdBy: P1, expiresAt: Timestamp.fromDate(new Date(Date.now() + 600000)) })));
  await check('parent cannot create pairing for another family', () =>
    assertFails(setDoc(doc(p1, 'pairings', '111111'), { familyId: 'famB', createdBy: P1, expiresAt: Timestamp.fromDate(new Date(Date.now() + 600000)) })));

  // Parent of family B cannot change family A's name
  await check('other parent cannot rename family A', () =>
    assertFails(updateDoc(doc(p2, 'families/famA'), { name: 'hacked' })));
  await check('parent can rename own family', () =>
    assertSucceeds(updateDoc(doc(p1, 'families/famA'), { name: 'Family A renamed' })));

  for (const [status, name] of results) console.log(status.padEnd(5), name);
  const failed = results.filter(r => r[0] === 'FAIL').length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  await env.cleanup();
  process.exit(failed ? 1 : 0);
})().catch(async (e) => {
  console.error('ERROR', e);
  if (env) await env.cleanup();
  process.exit(2);
});
