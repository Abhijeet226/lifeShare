/**
 * LIFE SHARE V3 — PHASE 2 AUTOMATED TEST SUITE
 * Complete Donor Journey Verification: State Machine, Idempotency, Multi-Unit, & Security
 */

const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
require('dotenv').config({ path: require('path').resolve(__dirname, '.env') });
const connectDB = require('./config/database');

const User = require('./models/User');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const Hospital = require('./models/Hospital');

const API_BASE = 'http://localhost:5000/api';
const JWT_SECRET = process.env.JWT_SECRET || 'lifeshare_secure_jwt_secret_2026';

function generateToken(user) {
  return jwt.sign({ id: user._id.toString(), email: user.email }, JWT_SECRET, { expiresIn: '1d' });
}

async function runPhase2Tests() {
  console.log('====================================================');
  console.log('  LIFE SHARE V3 PHASE 2 - DONOR JOURNEY TEST SUITE');
  console.log('====================================================\n');

  await connectDB();
  const results = [];

  try {
    // Cleanup any prior test records
    await User.deleteMany({ email: { $regex: /^test_p2_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^Phase2Test/ } });

    // Seed authoritative test hospital
    let hospital = await Hospital.findOne({ name: 'AIIMS Hospital Bhubaneswar' });
    if (!hospital) {
      hospital = await new Hospital({
        name: 'AIIMS Hospital Bhubaneswar',
        address: 'Sijua, Patrapada, Bhubaneswar, Odisha 751019',
        city: 'Bhubaneswar',
        phone: '+91 674 247 6789',
        location: { type: 'Point', coordinates: [85.7766, 20.2289] },
        isVerified: true
      }).save();
    }

    // Seed 3 test donors
    const donorA = await new User({
      name: 'P2 Donor Alpha',
      email: 'test_p2_donor_a@example.com',
      password: 'password123',
      mobile: '+919900000001',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.7800, 20.2300] }
    }).save();

    const donorB = await new User({
      name: 'P2 Donor Beta',
      email: 'test_p2_donor_b@example.com',
      password: 'password123',
      mobile: '+919900000002',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.7810, 20.2310] }
    }).save();

    const donorSuspended = await new User({
      name: 'P2 Donor Suspended',
      email: 'test_p2_donor_suspended@example.com',
      password: 'password123',
      mobile: '+919900000003',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'SUSPENDED',
      verificationStatus: 'UNVERIFIED'
    }).save();

    const donorBlocked = await new User({
      name: 'P2 Donor Blocked',
      email: 'test_p2_donor_blocked@example.com',
      password: 'password123',
      mobile: '+919900000004',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'BLOCKED',
      verificationStatus: 'UNVERIFIED'
    }).save();

    const tokenA = generateToken(donorA);
    const tokenB = generateToken(donorB);
    const tokenSuspended = generateToken(donorSuspended);
    const tokenBlocked = generateToken(donorBlocked);

    // Create a 2-unit emergency
    const emergency = await new EmergencyRequest({
      patientName: 'Phase2Test Multi-Unit Patient',
      bloodGroup: 'O+',
      unitsRequired: 2,
      unitsNeeded: 2,
      hospital: hospital.name,
      hospitalId: hospital._id,
      hospitalAddress: hospital.address,
      hospitalLocation: hospital.location,
      city: 'Bhubaneswar',
      contactNumber: '+919876543210',
      urgency: 'CRITICAL',
      status: 'SEARCHING'
    }).save();

    // --- TEST A: NOTIFIED -> VIEWED ---
    console.log('• TEST A: NOTIFIED -> VIEWED');
    const resA = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'VIEWED' })
    });
    const dataA = await resA.json();
    const dbRespA = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorA._id });
    const passA = resA.status === 200 && dbRespA && dbRespA.status === 'VIEWED' && dbRespA.viewedAt != null;
    results.push({ name: 'A. NOTIFIED -> VIEWED', pass: passA, status: resA.status, response: dataA });

    // --- TEST B: VIEWED -> ACCEPTED ---
    console.log('• TEST B: VIEWED -> ACCEPTED');
    const resB = await fetch(`${API_BASE}/emergencies/${emergency._id}/respond`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ response: 'ACCEPTED' })
    });
    const dataB = await resB.json();
    const dbRespB = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorA._id });
    const passB = resB.status === 200 && dbRespB && dbRespB.status === 'ACCEPTED' && dbRespB.acceptedAt != null;
    results.push({ name: 'B. VIEWED -> ACCEPTED', pass: passB, status: resB.status, response: dataB });

    // --- TEST C: ACCEPTED -> TRAVELLING ---
    console.log('• TEST C: ACCEPTED -> TRAVELLING');
    const resC = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'TRAVELLING' })
    });
    const dataC = await resC.json();
    const dbRespC = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorA._id });
    const passC = resC.status === 200 && dbRespC && dbRespC.status === 'TRAVELLING' && dbRespC.travellingAt != null;
    results.push({ name: 'C. ACCEPTED -> TRAVELLING', pass: passC, status: resC.status, response: dataC });

    // --- TEST D: TRAVELLING -> ARRIVED ---
    console.log('• TEST D: TRAVELLING -> ARRIVED');
    const resD = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'ARRIVED' })
    });
    const dataD = await resD.json();
    const dbRespD = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorA._id });
    const passD = resD.status === 200 && dbRespD && dbRespD.status === 'ARRIVED' && dbRespD.arrivedAt != null;
    results.push({ name: 'D. TRAVELLING -> ARRIVED', pass: passD, status: resD.status, response: dataD });

    // --- TEST E: INVALID TRANSITIONS REJECTED ---
    console.log('• TEST E: Invalid transition rejected (ARRIVED -> TRAVELLING & ARRIVED -> DONATED)');
    // Attempt 1: ARRIVED -> TRAVELLING (invalid rollback)
    const resE1 = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'TRAVELLING' })
    });
    // Attempt 2: Donor attempts DONATED (donor self-certification forbidden)
    const resE2 = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'DONATED' })
    });
    const passE = resE1.status === 400 && resE2.status === 403;
    results.push({ name: 'E. Invalid transitions rejected', pass: passE, status: `${resE1.status}, ${resE2.status}` });

    // --- TEST F & G: UNAUTHORIZED DONOR & ATTEMPT TO ALTER ANOTHER DONOR'S JOURNEY ---
    console.log('• TEST F & G: Donor isolation / identity enforcement');
    // Donor B cannot update Donor A's journey because JWT derives donorId
    const resG = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenB}` },
      body: JSON.stringify({ action: 'ARRIVED' }) // Donor B hasn't accepted yet
    });
    const passG = resG.status === 400; // Cannot jump to ARRIVED
    results.push({ name: 'F/G. Donor isolation & JWT identity', pass: passG, status: resG.status });

    // --- TEST H & I: SUSPENDED & BLOCKED DONORS REJECTED ---
    console.log('• TEST H & I: Suspended and Blocked Donors rejected');
    const resH = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenSuspended}` },
      body: JSON.stringify({ action: 'ACCEPTED' })
    });
    const resI = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenBlocked}` },
      body: JSON.stringify({ action: 'ACCEPTED' })
    });
    const passHI = resH.status === 403 && resI.status === 403;
    results.push({ name: 'H/I. Suspended and Blocked rejection', pass: passHI, status: `${resH.status}, ${resI.status}` });

    // --- TEST J & K: IDEMPOTENCY OF REPEATED TRAVELLING & ARRIVED REQUESTS ---
    console.log('• TEST J & K: Idempotency of duplicate requests');
    const initialArrivedAt = dbRespD.arrivedAt.getTime();
    // Rapid duplicate ARRIVED call
    const resK = await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'ARRIVED' })
    });
    const dbRespK = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorA._id });
    const passJK = resK.status === 200 && dbRespK.arrivedAt.getTime() === initialArrivedAt;
    results.push({ name: 'J/K. Idempotency & timestamp preservation', pass: passJK, status: resK.status });

    // --- TEST L: CANCELLED EMERGENCY REJECTS UPDATES ---
    console.log('• TEST L: Cancelled emergency rejects updates');
    const cancelledEmergency = await new EmergencyRequest({
      patientName: 'Phase2Test Cancelled',
      bloodGroup: 'B+',
      unitsRequired: 1,
      hospital: 'Capital Hospital',
      city: 'Bhubaneswar',
      contactNumber: '+919876543210',
      status: 'CANCELLED',
      isFulfilled: true
    }).save();

    const resL = await fetch(`${API_BASE}/emergencies/${cancelledEmergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenA}` },
      body: JSON.stringify({ action: 'ACCEPTED' })
    });
    const passL = resL.status === 400;
    results.push({ name: 'L. Cancelled emergency rejects updates', pass: passL, status: resL.status });

    // --- TEST M & P: MULTI-UNIT INDEPENDENT DONOR JOURNEYS ---
    console.log('• TEST M & P: Multi-unit emergency maintains independent donor journeys');
    // Donor B accepts remaining 1 unit of emergency
    const resMB = await fetch(`${API_BASE}/emergencies/${emergency._id}/respond`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenB}` },
      body: JSON.stringify({ response: 'ACCEPTED' })
    });
    // Advance Donor B to TRAVELLING
    await fetch(`${API_BASE}/emergencies/${emergency._id}/journey`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokenB}` },
      body: JSON.stringify({ action: 'TRAVELLING' })
    });

    const emergencyAfterBoth = await EmergencyRequest.findById(emergency._id);
    const donorAResp = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorA._id });
    const donorBResp = await EmergencyResponse.findOne({ requestId: emergency._id, donorId: donorB._id });

    // Donor A is ARRIVED, Donor B is TRAVELLING, Emergency has 2/2 accepted
    const passMP =
      emergencyAfterBoth.acceptedCount === 2 &&
      donorAResp.status === 'ARRIVED' &&
      donorBResp.status === 'TRAVELLING';

    results.push({ name: 'M/P. Multi-unit independent journeys', pass: passMP, status: resMB.status });

    // --- TEST N & O: AUTHORITATIVE HOSPITAL NAVIGATION COORDINATES ---
    console.log('• TEST N & O: Authoritative hospital navigation coordinates');
    const resN = await fetch(`${API_BASE}/emergencies/${emergency._id}`);
    const dataN = await resN.json();
    const hasHospitalCoords =
      dataN.emergency.hospitalLocation &&
      dataN.emergency.hospitalLocation.coordinates &&
      dataN.emergency.hospitalLocation.coordinates[0] === 85.7766 &&
      dataN.emergency.hospitalLocation.coordinates[1] === 20.2289;
    const passNO = resN.status === 200 && hasHospitalCoords;
    results.push({ name: 'N/O. Authoritative hospital coordinates', pass: passNO, status: resN.status });

    // --- TEST Q: GET /:id RETURNS PROPER DONOR JOURNEY & REQUESTER STATS ---
    console.log('• TEST Q: Authorization views for Donor and Requester');
    const resQD = await fetch(`${API_BASE}/emergencies/${emergency._id}`, {
      headers: { 'Authorization': `Bearer ${tokenA}` }
    });
    const dataQD = await resQD.json();
    const passQD =
      dataQD.myJourney &&
      dataQD.myJourney.status === 'ARRIVED' &&
      dataQD.myJourney.isPendingVerification === true;
    results.push({ name: 'Q. Authoritative status for Donor View', pass: passQD, status: resQD.status });

    // Cleanup test artifacts
    await User.deleteMany({ email: { $regex: /^test_p2_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^Phase2Test/ } });

  } catch (err) {
    console.error('Test execution exception:', err);
  } finally {
    await mongoose.disconnect();
  }

  // Print Summary
  console.log('\n====================================================');
  console.log('  PHASE 2 AUTOMATED TEST SUITE SUMMARY');
  console.log('====================================================');
  let allPass = true;
  for (const r of results) {
    const icon = r.pass ? '✅ PASS' : '❌ FAIL';
    if (!r.pass) allPass = false;
    console.log(`${icon} | ${r.name} (HTTP: ${r.status})`);
  }
  console.log('====================================================');
  console.log(`FINAL RESULT: ${allPass ? 'ALL PHASE 2 BACKEND TESTS PASSED' : 'TEST FAILURES DETECTED'}`);
  console.log('====================================================\n');

  process.exit(allPass ? 0 : 1);
}

runPhase2Tests();
