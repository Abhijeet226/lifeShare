/**
 * LIFE SHARE V3 - PHASE 3 AUTOMATED TEST SUITE
 * Complete Verification of Donation Certification, 90-Day Cooldown, History & Security
 */

const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
require('dotenv').config({ path: require('path').resolve(__dirname, '.env') });
const connectDB = require('./config/database');

const User = require('./models/User');
const Hospital = require('./models/Hospital');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const DonationHistory = require('./models/DonationHistory');
const { checkDonorEligibility, getCooldownCutoffDate, DONATION_COOLDOWN_DAYS } = require('./services/cooldownService');
const { findMatchingDonors } = require('./services/donorMatching');

const API_BASE = 'http://localhost:5000/api';
const JWT_SECRET = process.env.JWT_SECRET || 'lifeshare_secure_jwt_secret_2026';

function generateToken(user) {
  return jwt.sign({ id: user._id.toString(), email: user.email }, JWT_SECRET, { expiresIn: '1d' });
}

async function runPhase3Tests() {
  console.log('====================================================');
  console.log('  LIFE SHARE V3 PHASE 3 - DONATION & COOLDOWN SUITE');
  console.log('====================================================\n');

  await connectDB();
  const results = [];

  try {
    // Cleanup prior test artifacts
    await User.deleteMany({ email: { $regex: /^test_p3_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^Phase3Test/ } });
    await DonationHistory.deleteMany({ hospital: { $regex: /^Phase3/ } });

    // 1. Create two test hospitals
    const hospitalAIIMS = await new Hospital({
      name: 'Phase3 AIIMS Bhubaneswar',
      address: 'Patrapada, Bhubaneswar',
      city: 'Bhubaneswar',
      location: { type: 'Point', coordinates: [85.7766, 20.2289] },
      isVerified: true
    }).save();

    const hospitalCapital = await new Hospital({
      name: 'Phase3 Capital Hospital',
      address: 'Unit 6, Bhubaneswar',
      city: 'Bhubaneswar',
      location: { type: 'Point', coordinates: [85.8245, 20.2961] },
      isVerified: true
    }).save();

    // 2. Create Coordinators, Donors, and Requester
    const coordAIIMS = await new User({
      name: 'P3 Coordinator AIIMS',
      email: 'test_p3_coord_aiims@example.com',
      password: 'password123',
      mobile: '+919900000010',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      role: 'COORDINATOR',
      hospitalId: hospitalAIIMS._id,
      accountStatus: 'ACTIVE'
    }).save();

    const coordCapital = await new User({
      name: 'P3 Coordinator Capital',
      email: 'test_p3_coord_capital@example.com',
      password: 'password123',
      mobile: '+919900000011',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      role: 'COORDINATOR',
      hospitalId: hospitalCapital._id,
      accountStatus: 'ACTIVE'
    }).save();

    const coordBlocked = await new User({
      name: 'P3 Coordinator Blocked',
      email: 'test_p3_coord_blocked@example.com',
      password: 'password123',
      mobile: '+919900000012',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      role: 'COORDINATOR',
      hospitalId: hospitalAIIMS._id,
      accountStatus: 'BLOCKED'
    }).save();

    const requester = await new User({
      name: 'P3 Requester',
      email: 'test_p3_requester@example.com',
      password: 'password123',
      mobile: '+919900000013',
      bloodGroup: 'B+',
      city: 'Bhubaneswar',
      role: 'DONOR',
      accountStatus: 'ACTIVE'
    }).save();

    const donorA = await new User({
      name: 'P3 Donor Alpha',
      email: 'test_p3_donor_a@example.com',
      password: 'password123',
      mobile: '+919900000014',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.7800, 20.2300] }
    }).save();

    const donorB = await new User({
      name: 'P3 Donor Beta',
      email: 'test_p3_donor_b@example.com',
      password: 'password123',
      mobile: '+919900000015',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.7810, 20.2310] }
    }).save();

    const donorC = await new User({
      name: 'P3 Donor Gamma',
      email: 'test_p3_donor_c@example.com',
      password: 'password123',
      mobile: '+919900000016',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.7820, 20.2320] }
    }).save();

    const tokenCoordAIIMS = generateToken(coordAIIMS);
    const tokenCoordCapital = generateToken(coordCapital);
    const tokenCoordBlocked = generateToken(coordBlocked);
    const tokenRequester = generateToken(requester);
    const tokenDonorA = generateToken(donorA);
    const tokenDonorB = generateToken(donorB);
    const tokenDonorC = generateToken(donorC);

    // 3. Create Emergency at AIIMS (3 units needed)
    const emergencyAIIMS = await new EmergencyRequest({
      patientName: 'Phase3Test 3-Unit Patient',
      bloodGroup: 'O+',
      unitsRequired: 3,
      unitsNeeded: 3,
      hospital: hospitalAIIMS.name,
      hospitalId: hospitalAIIMS._id,
      hospitalAddress: hospitalAIIMS.address,
      hospitalLocation: hospitalAIIMS.location,
      city: 'Bhubaneswar',
      contactNumber: '+919876543210',
      requester: requester._id,
      postedBy: requester.email,
      urgency: 'CRITICAL',
      status: 'SEARCHING'
    }).save();

    // Advance Donor A, B, C through journey to ARRIVED
    for (const [donor, token] of [[donorA, tokenA = tokenDonorA], [donorB, tokenB = tokenDonorB], [donorC, tokenC = tokenDonorC]]) {
      await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/respond`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ response: 'ACCEPTED' })
      });
      await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/journey`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ action: 'TRAVELLING' })
      });
      await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/journey`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ action: 'ARRIVED' })
      });
    }

    // --- TEST 1: Donor cannot self-certify donation ---
    console.log('• TEST 1: Donor cannot self-certify donation');
    const res1 = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenDonorA}` },
      body: JSON.stringify({ donorId: donorA._id })
    });
    const pass1 = res1.status === 403;
    results.push({ name: '1. Donor cannot self-certify donation', pass: pass1, status: res1.status });

    // --- TEST 2: Requester cannot manufacture donation ---
    console.log('• TEST 2: Requester cannot verify donation');
    const res2 = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenRequester}` },
      body: JSON.stringify({ donorId: donorA._id })
    });
    const pass2 = res2.status === 403;
    results.push({ name: '2. Requester cannot verify donation', pass: pass2, status: res2.status });

    // --- TEST 3: Unauthorized coordinator for different hospital cannot verify ---
    console.log('• TEST 3: Coordinator for Capital Hospital cannot verify AIIMS emergency');
    const res3 = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenCoordCapital}` },
      body: JSON.stringify({ donorId: donorA._id })
    });
    const pass3 = res3.status === 403;
    results.push({ name: '3. Cross-hospital coordinator rejected', pass: pass3, status: res3.status });

    // --- TEST 4: Blocked coordinator cannot verify ---
    console.log('• TEST 4: Blocked coordinator rejected');
    const res4 = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenCoordBlocked}` },
      body: JSON.stringify({ donorId: donorA._id })
    });
    const pass4 = res4.status === 403;
    results.push({ name: '4. Blocked coordinator rejected', pass: pass4, status: res4.status });

    // --- TEST 5: Authorized coordinator verifies Donor A (1/3 units fulfilled) ---
    console.log('• TEST 5: Authorized coordinator verifies Donor A (1/3 fulfilled)');
    const res5 = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenCoordAIIMS}` },
      body: JSON.stringify({ donorId: donorA._id })
    });
    const data5 = await res5.json();
    const emergencyAfter1 = await EmergencyRequest.findById(emergencyAIIMS._id);
    const donorAInDb = await User.findById(donorA._id);
    console.log('Test 5 debug:', {
      status: res5.status,
      data5,
      emergencyUnits: emergencyAfter1 ? emergencyAfter1.unitsFulfilled : null,
      emergencyIsFulfilled: emergencyAfter1 ? emergencyAfter1.isFulfilled : null,
      donorDonationsCount: donorAInDb ? donorAInDb.donationsCount : null,
      donorLastDonation: donorAInDb ? donorAInDb.lastDonationDate : null
    });

    const pass5 =
      res5.status === 200 &&
      data5.success === true &&
      data5.unitsFulfilled === 1 &&
      emergencyAfter1.unitsFulfilled === 1 &&
      (emergencyAfter1.isFulfilled === false || !emergencyAfter1.isFulfilled) &&
      donorAInDb.donationsCount === 1 &&
      donorAInDb.lastDonationDate != null;

    results.push({ name: '5. Authorized coordinator verifies 1/3 donation', pass: pass5, status: res5.status });

    // --- TEST 6: Duplicate verification is idempotent ---
    console.log('• TEST 6: Duplicate verification is idempotent');
    const res6 = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenCoordAIIMS}` },
      body: JSON.stringify({ donorId: donorA._id })
    });
    const data6 = await res6.json();
    const pass6 = res6.status === 200 && data6.isAlreadyVerified === true && data6.certificateId === data5.certificateId;
    results.push({ name: '6. Duplicate verification is idempotent', pass: pass6, status: res6.status });

    // --- TEST 7: Multi-unit sequential verification (2/3 and 3/3 FULFILLED) ---
    console.log('• TEST 7: Multi-unit sequential verification');
    // Verify Donor B -> 2/3 fulfilled
    const res7B = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenCoordAIIMS}` },
      body: JSON.stringify({ donorId: donorB._id })
    });
    const data7B = await res7B.json();

    // Verify Donor C -> 3/3 fulfilled -> emergency.status = FULFILLED
    const res7C = await fetch(`${API_BASE}/emergencies/${emergencyAIIMS._id}/verify-donation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenCoordAIIMS}` },
      body: JSON.stringify({ donorId: donorC._id })
    });
    const data7C = await res7C.json();
    const emergencyFinal = await EmergencyRequest.findById(emergencyAIIMS._id);

    const pass7 =
      data7B.unitsFulfilled === 2 &&
      data7C.unitsFulfilled === 3 &&
      emergencyFinal.status === 'FULFILLED' &&
      emergencyFinal.isFulfilled === true;

    results.push({ name: '7. Multi-unit sequential fulfillment (3/3)', pass: pass7, status: res7C.status });

    // --- TEST 8: Public Certificate Verification Endpoint ---
    console.log('• TEST 8: Public Tamper-Proof Certificate Verification');
    const res8 = await fetch(`${API_BASE}/donations/verify/${data5.certificateId}`);
    const cert8 = await res8.json();
    console.log('Test 8 debug:', { status: res8.status, cert8 });

    const pass8 =
      res8.status === 200 &&
      cert8.valid === true &&
      cert8.certificateId === data5.certificateId &&
      cert8.donorName === donorA.name &&
      cert8.phone == null && // Privacy: NO phone exposed
      cert8.location == null; // Privacy: NO GPS exposed

    results.push({ name: '8. Tamper-proof public certificate verification', pass: pass8, status: res8.status });

    // --- TEST 9: Donor History Endpoint ---
    console.log('• TEST 9: Donor views own donation history');
    const res9 = await fetch(`${API_BASE}/donations/my-history`, {
      headers: { 'Authorization': `Bearer ${tokenDonorA}` }
    });
    const data9 = await res9.json();
    const pass9 = res9.status === 200 && data9.donations.length === 1 && data9.donations[0].certificateId === data5.certificateId;
    results.push({ name: '9. Donor views personal donation history', pass: pass9, status: res9.status });

    // --- TEST 10: 90-Day Post-Donation Cooldown Policy Boundaries ---
    console.log('• TEST 10: 90-Day Cooldown Policy Evaluation');
    const now = new Date();
    const todayResult = checkDonorEligibility({ lastDonationDate: now }, now);
    const days89Ago = new Date(now.getTime() - (89 * 24 * 60 * 60 * 1000));
    const result89 = checkDonorEligibility({ lastDonationDate: days89Ago }, now);
    const days90Ago = new Date(now.getTime() - (90 * 24 * 60 * 60 * 1000));
    const result90 = checkDonorEligibility({ lastDonationDate: days90Ago }, now);
    const days91Ago = new Date(now.getTime() - (91 * 24 * 60 * 60 * 1000));
    const result91 = checkDonorEligibility({ lastDonationDate: days91Ago }, now);

    const pass10 =
      todayResult.isEligible === false &&
      result89.isEligible === false && result89.daysRemaining === 1 &&
      result90.isEligible === true &&
      result91.isEligible === true;

    results.push({ name: '10. 90-Day Cooldown boundary calculations', pass: pass10, status: 'N/A' });

    // --- TEST 11: Ineligible donor attempting to respond is rejected with 403 ---
    console.log('• TEST 11: Ineligible donor cannot accept new emergency');
    const newEmergency = await new EmergencyRequest({
      patientName: 'Phase3Test New Emergency',
      bloodGroup: 'O+',
      unitsRequired: 1,
      hospital: hospitalAIIMS.name,
      city: 'Bhubaneswar',
      contactNumber: '+919876543210',
      status: 'SEARCHING'
    }).save();

    // Donor A just donated in TEST 5 -> currently in cooldown!
    const res11 = await fetch(`${API_BASE}/emergencies/${newEmergency._id}/respond`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenDonorA}` },
      body: JSON.stringify({ response: 'ACCEPTED' })
    });
    const pass11 = res11.status === 403;
    results.push({ name: '11. Cooldown blocks emergency acceptance (403)', pass: pass11, status: res11.status });

    // --- TEST 12: Ineligible donor excluded from matching engine query ---
    console.log('• TEST 12: Ineligible donor excluded from matching engine');
    const matchResult = await findMatchingDonors(newEmergency._id, 0);
    const donorAIncluded = matchResult.matched && matchResult.matched.some(m => (m.id || m._id || '').toString() === donorA._id.toString());
    const pass12 = !donorAIncluded;
    results.push({ name: '12. Cooldown excludes donor from matching engine', pass: pass12, status: 'N/A' });

    // --- TEST 13: Donor cannot tamper with lastDonationDate or donationsCount ---
    console.log('• TEST 13: Donor cannot manipulate donation counters');
    await fetch(`${API_BASE}/users/profile`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenDonorA}` },
      body: JSON.stringify({ lastDonationDate: null, donationsCount: 99, role: 'COORDINATOR' })
    });
    const donorAPostAttack = await User.findById(donorA._id);
    const pass13 =
      donorAPostAttack.donationsCount === 1 &&
      donorAPostAttack.lastDonationDate != null &&
      donorAPostAttack.role === 'DONOR';

    results.push({ name: '13. Immutability of donation audit fields', pass: pass13, status: 200 });

    // Cleanup
    await User.deleteMany({ email: { $regex: /^test_p3_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^Phase3Test/ } });
    await DonationHistory.deleteMany({ hospital: { $regex: /^Phase3/ } });

  } catch (err) {
    console.error('Phase 3 test execution error:', err);
  } finally {
    await mongoose.disconnect();
  }

  // Summary
  console.log('\n====================================================');
  console.log('  PHASE 3 AUTOMATED TEST SUITE SUMMARY');
  console.log('====================================================');
  let allPass = true;
  for (const r of results) {
    const icon = r.pass ? '✅ PASS' : '❌ FAIL';
    if (!r.pass) allPass = false;
    console.log(`${icon} | ${r.name} (HTTP: ${r.status})`);
  }
  console.log('====================================================');
  console.log(`FINAL RESULT: ${allPass ? 'ALL PHASE 3 TESTS PASSED' : 'TEST FAILURES DETECTED'}`);
  console.log('====================================================\n');

  process.exit(allPass ? 0 : 1);
}

runPhase3Tests();
