/**
 * LIFE SHARE V3 — PHASE 1 FINAL SECURITY RECHECK
 *
 * Verification Suite:
 * 1. Suspended / Blocked Donor Response
 * 2. Matching Verification (Active vs Suspended vs Blocked)
 * 3. Verify Trust Status & Account Status Cannot Be Self-Modified
 */

const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
require('dotenv').config({ path: require('path').resolve(__dirname, '.env') });
const connectDB = require('./config/database');

const User = require('./models/User');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const { findMatchingDonors } = require('./services/donorMatching');

const API_BASE = 'http://localhost:5000/api';
const JWT_SECRET = process.env.JWT_SECRET || 'lifeshare_secure_jwt_secret_2026';

function generateToken(user) {
  return jwt.sign({ id: user._id.toString(), email: user.email }, JWT_SECRET, { expiresIn: '1d' });
}

async function runSecurityRecheck() {
  console.log('================================================================');
  console.log('🛡️  LIFE SHARE V3 — PHASE 1 FINAL SECURITY RECHECK');
  console.log('================================================================\n');

  await connectDB();
  console.log('Connected to MongoDB Atlas successfully.\n');

  const results = [];

  try {
    // Clean up any stale test fixtures
    await User.deleteMany({ email: { $regex: /^test_security_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^SecurityTest/ } });

    // =========================================================================
    // SECTION 1: SUSPENDED / BLOCKED DONOR RESPONSE
    // =========================================================================
    console.log('----------------------------------------------------------------');
    console.log('SECTION 1: SUSPENDED / BLOCKED DONOR RESPONSE');
    console.log('----------------------------------------------------------------');

    // 1.1 Create Test Emergency
    const emergency1 = await new EmergencyRequest({
      patientName: 'SecurityTest Patient 1',
      hospital: 'AIIMS Hospital Bhubaneswar',
      bloodGroup: 'B+',
      unitsRequired: 2,
      unitsNeeded: 2,
      city: 'Bhubaneswar',
      contactNumber: '+919876543210',
      urgency: 'CRITICAL',
      status: 'SEARCHING',
      isFulfilled: false,
      hospitalLocation: { type: 'Point', coordinates: [85.8245, 20.2961] }
    }).save();

    // 1.2 Create Eligible Test Donor
    const testDonor = await new User({
      name: 'Security Test Donor',
      email: 'test_security_donor@example.com',
      password: 'hashedpassword123',
      mobile: '+919999988888',
      bloodGroup: 'B+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.8250, 20.2970] }
    }).save();

    const donorToken = generateToken(testDonor);

    // Test 1.A: Set accountStatus = ACTIVE -> POST /api/emergencies/:id/respond
    let res1A = await fetch(`${API_BASE}/emergencies/${emergency1._id}/respond`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${donorToken}`
      },
      body: JSON.stringify({ response: 'ACCEPTED' })
    });
    let data1A = await res1A.json();
    let dbRecord1A = await EmergencyResponse.findOne({ requestId: emergency1._id, donorId: testDonor._id });

    const pass1A = res1A.status === 200 && data1A.success === true && dbRecord1A && dbRecord1A.status === 'ACCEPTED';
    results.push({
      test: '1.A: Active Donor Response',
      httpStatus: res1A.status,
      expectedStatus: 200,
      actualResponse: JSON.stringify(data1A),
      dbState: `Response in DB: ${dbRecord1A ? dbRecord1A.status : 'None'}`,
      reason: 'Active donor successfully authorized to accept active emergency',
      pass: pass1A
    });

    // Reset emergency and response for 1.B
    await EmergencyResponse.deleteMany({ requestId: emergency1._id });

    // Test 1.B: Set SAME donor accountStatus = SUSPENDED -> POST /api/emergencies/:id/respond
    await User.findByIdAndUpdate(testDonor._id, { accountStatus: 'SUSPENDED' });

    let res1B = await fetch(`${API_BASE}/emergencies/${emergency1._id}/respond`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${donorToken}`
      },
      body: JSON.stringify({ response: 'ACCEPTED' })
    });
    let data1B = await res1B.json();
    let dbRecord1B = await EmergencyResponse.findOne({ requestId: emergency1._id, donorId: testDonor._id });

    const pass1B = res1B.status === 403 && data1B.success === false && data1B.message.toLowerCase().includes('suspended') && !dbRecord1B;
    results.push({
      test: '1.B: Suspended Donor Response',
      httpStatus: res1B.status,
      expectedStatus: 403,
      actualResponse: JSON.stringify(data1B),
      dbState: `Response in DB: ${dbRecord1B ? dbRecord1B.status : 'None (Blocked from creation)'}`,
      reason: 'Suspended account explicitly rejected with 403 Forbidden',
      pass: pass1B
    });

    // Test 1.C: Set SAME donor accountStatus = BLOCKED -> POST /api/emergencies/:id/respond
    await User.findByIdAndUpdate(testDonor._id, { accountStatus: 'BLOCKED' });

    let res1C = await fetch(`${API_BASE}/emergencies/${emergency1._id}/respond`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${donorToken}`
      },
      body: JSON.stringify({ response: 'ACCEPTED' })
    });
    let data1C = await res1C.json();
    let dbRecord1C = await EmergencyResponse.findOne({ requestId: emergency1._id, donorId: testDonor._id });

    const pass1C = res1C.status === 403 && data1C.success === false && data1C.message.toLowerCase().includes('blocked') && !dbRecord1C;
    results.push({
      test: '1.C: Blocked Donor Response',
      httpStatus: res1C.status,
      expectedStatus: 403,
      actualResponse: JSON.stringify(data1C),
      dbState: `Response in DB: ${dbRecord1C ? dbRecord1C.status : 'None (Blocked from creation)'}`,
      reason: 'Blocked account explicitly rejected with 403 Forbidden',
      pass: pass1C
    });

    // =========================================================================
    // SECTION 2: MATCHING ENGINE VERIFICATION
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('SECTION 2: MATCHING ENGINE VERIFICATION (ACTIVE vs SUSPENDED vs BLOCKED)');
    console.log('----------------------------------------------------------------');

    // 2.1 Create Matching Test Emergency
    const emergency2 = await new EmergencyRequest({
      patientName: 'SecurityTest Patient 2',
      hospital: 'Capital Hospital Bhubaneswar',
      bloodGroup: 'O+',
      unitsRequired: 1,
      unitsNeeded: 1,
      city: 'Bhubaneswar',
      contactNumber: '+919876543211',
      urgency: 'URGENT',
      status: 'SEARCHING',
      isFulfilled: false,
      hospitalLocation: { type: 'Point', coordinates: [85.8245, 20.2961] }
    }).save();

    // 2.2 Create Test Candidate Donor
    const matchingDonor = await new User({
      name: 'Security Matching Candidate',
      email: 'test_security_matching_candidate@example.com',
      password: 'password123',
      mobile: '+919999977777',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      location: { type: 'Point', coordinates: [85.8248, 20.2965] },
      locationUpdatedAt: new Date()
    }).save();

    // Test 2.A: ACTIVE Donor -> eligible for matching
    await EmergencyResponse.deleteMany({ requestId: emergency2._id });
    await User.findByIdAndUpdate(matchingDonor._id, { accountStatus: 'ACTIVE' });
    const matchResultActive = await findMatchingDonors(emergency2._id, 0);
    const donorMatchedActive = matchResultActive.matched && matchResultActive.matched.some(m => (m.id || m._id || '').toString() === matchingDonor._id.toString());

    const pass2A = donorMatchedActive;
    results.push({
      test: '2.A: ACTIVE Donor in Matching',
      httpStatus: 'N/A (Engine / DB Query)',
      expectedStatus: 'Included in Match',
      actualResponse: `Matched ${matchResultActive.matched ? matchResultActive.matched.length : 0} donors. Donor Included: ${donorMatchedActive}`,
      dbState: `Donor accountStatus: ACTIVE, isAvailable: true`,
      reason: 'Active donor matched via MongoDB $geoNear query with status filter',
      pass: pass2A
    });

    // Test 2.B: SUSPENDED Donor -> excluded from matching
    await EmergencyResponse.deleteMany({ requestId: emergency2._id });
    await User.findByIdAndUpdate(matchingDonor._id, { accountStatus: 'SUSPENDED' });
    const matchResultSuspended = await findMatchingDonors(emergency2._id, 0);
    const donorMatchedSuspended = matchResultSuspended.matched && matchResultSuspended.matched.some(m => (m.id || m._id || '').toString() === matchingDonor._id.toString());

    const pass2B = !donorMatchedSuspended;
    results.push({
      test: '2.B: SUSPENDED Donor Excluded from Matching',
      httpStatus: 'N/A (Engine / DB Query)',
      expectedStatus: 'Excluded from Match',
      actualResponse: `Matched ${matchResultSuspended.matched ? matchResultSuspended.matched.length : 0} donors. Donor Included: ${donorMatchedSuspended}`,
      dbState: `Donor accountStatus: SUSPENDED`,
      reason: 'Suspended donor strictly excluded by accountStatus: "ACTIVE" MongoDB query filter',
      pass: pass2B
    });

    // Test 2.C: BLOCKED Donor -> excluded from matching
    await EmergencyResponse.deleteMany({ requestId: emergency2._id });
    await User.findByIdAndUpdate(matchingDonor._id, { accountStatus: 'BLOCKED' });
    const matchResultBlocked = await findMatchingDonors(emergency2._id, 0);
    const donorMatchedBlocked = matchResultBlocked.matched && matchResultBlocked.matched.some(m => (m.id || m._id || '').toString() === matchingDonor._id.toString());

    const pass2C = !donorMatchedBlocked;
    results.push({
      test: '2.C: BLOCKED Donor Excluded from Matching',
      httpStatus: 'N/A (Engine / DB Query)',
      expectedStatus: 'Excluded from Match',
      actualResponse: `Matched ${matchResultBlocked.matched ? matchResultBlocked.matched.length : 0} donors. Donor Included: ${donorMatchedBlocked}`,
      dbState: `Donor accountStatus: BLOCKED`,
      reason: 'Blocked donor strictly excluded by accountStatus: "ACTIVE" MongoDB query filter',
      pass: pass2C
    });

    // =========================================================================
    // SECTION 3: VERIFY TRUST STATUS CANNOT BE SELF-MODIFIED
    // =========================================================================
    console.log('\n----------------------------------------------------------------');
    console.log('SECTION 3: VERIFY TRUST STATUS & ACCOUNT STATUS CANNOT BE SELF-MODIFIED');
    console.log('----------------------------------------------------------------');

    // 3.1 Create Suspended, Unverified User
    const restrictedUser = await new User({
      name: 'Restricted User',
      email: 'test_security_restricted@example.com',
      password: 'password123',
      mobile: '+919999966666',
      bloodGroup: 'A+',
      city: 'Bhubaneswar',
      isAvailable: true,
      accountStatus: 'SUSPENDED',
      verificationStatus: 'UNVERIFIED',
      phoneVerified: false,
      emailVerified: false
    }).save();

    const restrictedToken = generateToken(restrictedUser);

    // 3.2 Attempt Self-Escalation via PUT /api/users/profile
    const attackPayload = {
      name: 'Restricted User Hacker',
      verificationStatus: 'DONOR_VERIFIED',
      accountStatus: 'ACTIVE',
      phoneVerified: true,
      emailVerified: true
    };

    const res3 = await fetch(`${API_BASE}/users/profile`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${restrictedToken}`
      },
      body: JSON.stringify(attackPayload)
    });
    const data3 = await res3.json();

    // 3.3 Fetch authoritative state directly from MongoDB
    const userInDbAfterAttack = await User.findById(restrictedUser._id);

    const checkVerificationStatus = userInDbAfterAttack.verificationStatus === 'UNVERIFIED';
    const checkAccountStatus = userInDbAfterAttack.accountStatus === 'SUSPENDED';
    const checkPhoneVerified = userInDbAfterAttack.phoneVerified === false;
    const checkEmailVerified = userInDbAfterAttack.emailVerified === false;
    const pass3 = checkVerificationStatus && checkAccountStatus && checkPhoneVerified && checkEmailVerified;

    results.push({
      test: '3: Trust & Account Status Immutability',
      httpStatus: res3.status,
      expectedStatus: 200,
      actualResponse: JSON.stringify({
        verificationStatusInResponse: data3.user ? data3.user.verificationStatus : 'N/A',
        accountStatusInResponse: data3.user ? data3.user.accountStatus : 'N/A'
      }),
      dbState: `DB verificationStatus: ${userInDbAfterAttack.verificationStatus}, accountStatus: ${userInDbAfterAttack.accountStatus}, phoneVerified: ${userInDbAfterAttack.phoneVerified}, emailVerified: ${userInDbAfterAttack.emailVerified}`,
      reason: 'Server whitelist rejects forbidden fields; trust status and accountStatus cannot be self-escalated',
      pass: pass3
    });

    // Cleanup test data
    await User.deleteMany({ email: { $regex: /^test_security_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^SecurityTest/ } });

  } catch (err) {
    console.error('Execution error during security recheck:', err);
  } finally {
    await mongoose.disconnect();
  }

  // Print results summary
  console.log('\n================================================================');
  console.log('📋 FINAL SECURITY RECHECK REPORT');
  console.log('================================================================');
  let allPassed = true;
  for (const r of results) {
    const statusLabel = r.pass ? '✅ PASS' : '❌ FAIL';
    if (!r.pass) allPassed = false;
    console.log(`\n[${statusLabel}] ${r.test}`);
    console.log(`  • HTTP Status:     ${r.httpStatus}`);
    console.log(`  • Actual Response: ${r.actualResponse}`);
    console.log(`  • Database State:  ${r.dbState}`);
    console.log(`  • Reason:          ${r.reason}`);
  }

  console.log('\n================================================================');
  console.log(`FINAL VERDICT: ${allPassed ? 'ALL SECURITY RECHECKS PASSED (PASS)' : 'SECURITY DEFECTS FOUND (FAIL)'}`);
  console.log('================================================================\n');

  process.exit(allPassed ? 0 : 1);
}

runSecurityRecheck();
