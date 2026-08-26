/**
 * LIFE SHARE V3 - PHASE 4 AUTOMATED TEST SUITE
 * Complete Verification of Admin & Coordinator Operations, RBAC Middleware, Bi-directional Sync, and Security
 */

require('dotenv').config({ path: require('path').join(__dirname, '.env') });
const mongoose = require('mongoose');
const http = require('http');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');

const connectDB = require('./config/database');
const User = require('./models/User');
const Hospital = require('./models/Hospital');
const City = require('./models/City');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const DonationHistory = require('./models/DonationHistory');
const AuditLog = require('./models/AuditLog');
const { verifyCertificateIntegrity } = require('./services/certificateService');

const JWT_SECRET = process.env.JWT_SECRET || 'lifeshare_secure_jwt_secret_2026';
const PORT = process.env.PORT || 5000;

function createToken(user) {
  return jwt.sign({ id: user._id.toString(), email: user.email }, JWT_SECRET, { expiresIn: '1d' });
}

function request(options, data) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: body ? JSON.parse(body) : {} });
        } catch (e) {
          resolve({ status: res.statusCode, body: { raw: body } });
        }
      });
    });
    req.on('error', reject);
    if (data) req.write(JSON.stringify(data));
    req.end();
  });
}

const testResults = [];

function assert(condition, message) {
  if (!condition) {
    testResults.push({ pass: false, message });
    console.error(`❌ FAIL: ${message}`);
  } else {
    testResults.push({ pass: true, message });
    console.log(`✅ PASS: ${message}`);
  }
}

async function runPhase4Tests() {
  console.log('====================================================');
  console.log('  LIFE SHARE V3 — PHASE 4 ADMIN & COORDINATOR SUITE');
  console.log('====================================================\n');

  await connectDB();
  const timestamp = Date.now();

  try {
    // 0. Clean up previous test records
    await User.deleteMany({ email: { $regex: /^test_p4_/ } });
    await Hospital.deleteMany({ name: { $regex: /^Phase4_/ } });
    await EmergencyRequest.deleteMany({ patientName: { $regex: /^P4Patient_/ } });
    await DonationHistory.deleteMany({ patientName: { $regex: /^P4Patient_/ } });

    // 1. Create Test City
    const city = await City.findOne({ normalizedName: 'bhubaneswar' }) ||
      await new City({
        name: 'Bhubaneswar',
        stateName: 'Odisha',
        location: { type: 'Point', coordinates: [85.8245, 20.2961] }
      }).save();

    // 2. Create Test Hospitals (Hospital A and Hospital B)
    const hospitalA = await new Hospital({
      name: `Phase4_Hospital_A_${timestamp}`,
      address: 'Khandagiri, Bhubaneswar',
      cityId: city._id,
      location: { type: 'Point', coordinates: [85.7891, 20.2587] },
      isVerified: true
    }).save();

    const hospitalB = await new Hospital({
      name: `Phase4_Hospital_B_${timestamp}`,
      address: 'Chandrasekharpur, Bhubaneswar',
      cityId: city._id,
      location: { type: 'Point', coordinates: [85.8189, 20.3245] },
      isVerified: true
    }).save();

    // 3. Create Users with Different Roles & Statuses
    const hash = await bcrypt.hash('Password@123', 10);

    const adminUser = await new User({
      name: 'P4 Admin',
      email: `test_p4_admin_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9111111111',
      bloodGroup: 'O+',
      role: 'ADMIN',
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    const suspendedAdmin = await new User({
      name: 'P4 Suspended Admin',
      email: `test_p4_susp_admin_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9222222222',
      bloodGroup: 'O+',
      role: 'ADMIN',
      accountStatus: 'SUSPENDED',
      cityId: city._id
    }).save();

    const blockedAdmin = await new User({
      name: 'P4 Blocked Admin',
      email: `test_p4_blk_admin_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9333333333',
      bloodGroup: 'O+',
      role: 'ADMIN',
      accountStatus: 'BLOCKED',
      cityId: city._id
    }).save();

    const coordHospitalA = await new User({
      name: 'P4 Coordinator Hospital A',
      email: `test_p4_coord_a_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9444444444',
      bloodGroup: 'B+',
      role: 'COORDINATOR',
      hospitalId: hospitalA._id,
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    // Add coord to Hospital A
    hospitalA.authorizedCoordinatorIds.push(coordHospitalA._id);
    await hospitalA.save();

    const coordHospitalB = await new User({
      name: 'P4 Coordinator Hospital B',
      email: `test_p4_coord_b_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9555555555',
      bloodGroup: 'B+',
      role: 'COORDINATOR',
      hospitalId: hospitalB._id,
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    hospitalB.authorizedCoordinatorIds.push(coordHospitalB._id);
    await hospitalB.save();

    const donorUser = await new User({
      name: 'P4 Voluntary Donor',
      email: `test_p4_donor_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9666666666',
      bloodGroup: 'O+',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    const requesterUser = await new User({
      name: 'P4 Requester',
      email: `test_p4_requester_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9777777777',
      bloodGroup: 'A+',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    const candidateUser = await new User({
      name: 'P4 Promotion Candidate',
      email: `test_p4_candidate_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9888888888',
      bloodGroup: 'AB+',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    // Tokens
    const adminToken = createToken(adminUser);
    const suspendedAdminToken = createToken(suspendedAdmin);
    const blockedAdminToken = createToken(blockedAdmin);
    const coordAToken = createToken(coordHospitalA);
    const coordBToken = createToken(coordHospitalB);
    const donorToken = createToken(donorUser);
    const requesterToken = createToken(requesterUser);

    console.log('--- SECTION 1: ROLE-BASED ACCESS CONTROL (RBAC) ---');

    // TEST 1: DONOR cannot access admin endpoints
    const resDonorOnAdmin = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/admin/users',
      method: 'GET',
      headers: { Authorization: `Bearer ${donorToken}` }
    });
    assert(resDonorOnAdmin.status === 403, 'TEST 1: DONOR cannot access /api/admin/users (HTTP 403)');

    // TEST 2: COORDINATOR cannot access admin endpoints
    const resCoordOnAdmin = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/admin/users',
      method: 'GET',
      headers: { Authorization: `Bearer ${coordAToken}` }
    });
    assert(resCoordOnAdmin.status === 403, 'TEST 2: COORDINATOR cannot access /api/admin/users (HTTP 403)');

    // TEST 3: ADMIN can access admin endpoints
    const resAdminOnAdmin = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/admin/users',
      method: 'GET',
      headers: { Authorization: `Bearer ${adminToken}` }
    });
    assert(resAdminOnAdmin.status === 200 && resAdminOnAdmin.body.success, 'TEST 3: ADMIN can access /api/admin/users (HTTP 200)');

    // TEST 4: Suspended ADMIN cannot perform protected operations
    const resSuspAdmin = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/admin/stats',
      method: 'GET',
      headers: { Authorization: `Bearer ${suspendedAdminToken}` }
    });
    assert(resSuspAdmin.status === 403, 'TEST 4: Suspended ADMIN cannot access admin operations (HTTP 403)');

    // TEST 5: Blocked ADMIN cannot perform protected operations
    const resBlkAdmin = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/admin/stats',
      method: 'GET',
      headers: { Authorization: `Bearer ${blockedAdminToken}` }
    });
    assert(resBlkAdmin.status === 403, 'TEST 5: Blocked ADMIN cannot access admin operations (HTTP 403)');

    console.log('\n--- SECTION 2: HOSPITAL-SCOPED COORDINATOR OPERATIONS ---');

    // Create Emergency for Hospital A
    const emergencyA = await new EmergencyRequest({
      requester: requesterUser._id,
      patientName: `P4Patient_A_${timestamp}`,
      bloodGroup: 'O+',
      unitsRequired: 1,
      hospitalId: hospitalA._id,
      hospital: hospitalA.name,
      hospitalLocation: hospitalA.location,
      cityId: city._id,
      contactNumber: '+91 9777777777',
      status: 'SEARCHING'
    }).save();

    // Donor responds and transitions to ARRIVED
    const respA = await new EmergencyResponse({
      requestId: emergencyA._id,
      donorId: donorUser._id,
      status: 'ARRIVED',
      arrivedAt: new Date()
    }).save();

    // TEST 6: Active coordinator can view pending verifications for own hospital
    const resCoordAPending = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/emergencies/coordinator/pending-verifications',
      method: 'GET',
      headers: { Authorization: `Bearer ${coordAToken}` }
    });
    assert(
      resCoordAPending.status === 200 &&
      resCoordAPending.body.pendingVerifications &&
      resCoordAPending.body.pendingVerifications.some(i => i.patientName === emergencyA.patientName),
      'TEST 6: Active coordinator can access pending arrived donors for own hospital'
    );

    // TEST 7: Coordinator B cannot see Hospital A donors in pending verifications
    const resCoordBPending = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/emergencies/coordinator/pending-verifications',
      method: 'GET',
      headers: { Authorization: `Bearer ${coordBToken}` }
    });
    assert(
      resCoordBPending.status === 200 &&
      (!resCoordBPending.body.pendingVerifications ||
        !resCoordBPending.body.pendingVerifications.some(i => i.patientName === emergencyA.patientName)),
      'TEST 7: Coordinator B cannot view arrived donors from Hospital A'
    );

    // TEST 8: Coordinator B cannot verify Hospital A's donation (Cross-hospital forbidden)
    const resCoordBVerifyA = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/emergencies/${emergencyA._id}/verify-donation`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${coordBToken}`,
        'Content-Type': 'application/json'
      }
    }, { donorId: donorUser._id.toString() });
    assert(resCoordBVerifyA.status === 403, 'TEST 8: Coordinator B forbidden from verifying Hospital A donation (HTTP 403)');

    // Create a non-arrived response for non-arrived test
    const donorNonArrived = await new User({
      name: 'P4 Non-Arrived Donor',
      email: `test_p4_nonarr_${timestamp}@example.com`,
      password: hash,
      mobile: '+91 9999999999',
      bloodGroup: 'O+',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      cityId: city._id
    }).save();

    await new EmergencyResponse({
      requestId: emergencyA._id,
      donorId: donorNonArrived._id,
      status: 'ACCEPTED' // NOT ARRIVED
    }).save();

    // TEST 9: Coordinator cannot verify non-ARRIVED donor
    const resVerifyNonArrived = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/emergencies/${emergencyA._id}/verify-donation`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${coordAToken}`,
        'Content-Type': 'application/json'
      }
    }, { donorId: donorNonArrived._id.toString() });
    assert(resVerifyNonArrived.status === 400, 'TEST 9: Coordinator cannot verify non-ARRIVED donor (HTTP 400)');

    // TEST 10: Donor cannot self-certify
    const resDonorSelfCert = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/emergencies/${emergencyA._id}/verify-donation`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${donorToken}`,
        'Content-Type': 'application/json'
      }
    }, { donorId: donorUser._id.toString() });
    assert(resDonorSelfCert.status === 403, 'TEST 10: Donor cannot self-certify donation (HTTP 403)');

    // TEST 11: Requester cannot certify donation
    const resReqCert = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/emergencies/${emergencyA._id}/verify-donation`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${requesterToken}`,
        'Content-Type': 'application/json'
      }
    }, { donorId: donorUser._id.toString() });
    assert(resReqCert.status === 403, 'TEST 11: Requester cannot certify donation (HTTP 403)');

    // TEST 12: Coordinator A verifies valid ARRIVED donor
    const resCoordAVerify = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/emergencies/${emergencyA._id}/verify-donation`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${coordAToken}`,
        'Content-Type': 'application/json'
      }
    }, { donorId: donorUser._id.toString() });
    assert(
      resCoordAVerify.status === 200 &&
      resCoordAVerify.body.success &&
      resCoordAVerify.body.certificateId,
      'TEST 12: Coordinator A successfully verified ARRIVED donor and generated certificate'
    );

    // TEST 13: Duplicate donation verification remains idempotent
    const resDuplicateVerify = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/emergencies/${emergencyA._id}/verify-donation`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${coordAToken}`,
        'Content-Type': 'application/json'
      }
    }, { donorId: donorUser._id.toString() });
    assert(
      resDuplicateVerify.status === 200 &&
      (resDuplicateVerify.body.isAlreadyVerified || resDuplicateVerify.body.success),
      'TEST 13: Duplicate donation verification is strictly idempotent (HTTP 200)'
    );

    // TEST 14: Certificate cryptographic integrity check remains valid
    const createdCert = await DonationHistory.findOne({ certificateId: resCoordAVerify.body.certificateId });
    assert(
      createdCert && verifyCertificateIntegrity(createdCert),
      'TEST 14: Generated certificate cryptographic HMAC-SHA256 integrity verified'
    );

    console.log('\n--- SECTION 3: SECURITY WHITELISTING & PRIVILEGE ESCALATION ---');

    // TEST 15: Donor cannot self-promote to ADMIN or COORDINATOR via /api/users/profile
    const resDonorProfileHack = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/users/profile',
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${donorToken}`,
        'Content-Type': 'application/json'
      }
    }, { role: 'ADMIN', verificationStatus: 'DONOR_VERIFIED', accountStatus: 'ACTIVE', donationsCount: 999 });
    const refetchedDonor = await User.findById(donorUser._id);
    assert(
      refetchedDonor.role === 'DONOR',
      'TEST 15: Donor cannot self-promote to ADMIN via profile update (whitelist enforced)'
    );

    // TEST 16: Coordinator cannot modify own role or hospitalId via profile update
    const resCoordProfileHack = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/users/profile',
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${coordAToken}`,
        'Content-Type': 'application/json'
      }
    }, { role: 'ADMIN', hospitalId: hospitalB._id.toString() });
    const refetchedCoord = await User.findById(coordHospitalA._id);
    assert(
      refetchedCoord.role === 'COORDINATOR' && String(refetchedCoord.hospitalId) === String(hospitalA._id),
      'TEST 16: Coordinator cannot self-escalate role or change hospitalId via profile update'
    );

    console.log('\n--- SECTION 4: ADMIN OPERATIONS & BI-DIRECTIONAL COORDINATOR SYNC ---');

    // TEST 17: Admin promotes user to COORDINATOR and assigns to Hospital B
    const resAssignCoord = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/admin/hospitals/${hospitalB._id}/coordinators`,
      method: 'POST',
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json'
      }
    }, { coordinatorId: candidateUser._id.toString() });
    assert(resAssignCoord.status === 200 && resAssignCoord.body.success, 'TEST 17: Admin assigned coordinator to Hospital B');

    // Verify bi-directional sync in DB
    const updatedCandidate = await User.findById(candidateUser._id);
    const updatedHospB = await Hospital.findById(hospitalB._id);
    assert(
      updatedCandidate.role === 'COORDINATOR' &&
      String(updatedCandidate.hospitalId) === String(hospitalB._id) &&
      updatedHospB.authorizedCoordinatorIds.some(id => String(id) === String(candidateUser._id)),
      'TEST 18: Bi-directional sync verified in Hospital.authorizedCoordinatorIds and User.hospitalId'
    );

    // TEST 19: Admin removes coordinator from Hospital B
    const resRemoveCoord = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/admin/hospitals/${hospitalB._id}/coordinators/${candidateUser._id}`,
      method: 'DELETE',
      headers: { Authorization: `Bearer ${adminToken}` }
    });
    assert(resRemoveCoord.status === 200 && resRemoveCoord.body.success, 'TEST 19: Admin removed coordinator from Hospital B');

    const syncedUserAfterRemoval = await User.findById(candidateUser._id);
    const syncedHospAfterRemoval = await Hospital.findById(hospitalB._id);
    assert(
      syncedUserAfterRemoval.hospitalId === null &&
      !syncedHospAfterRemoval.authorizedCoordinatorIds.some(id => String(id) === String(candidateUser._id)),
      'TEST 20: Bi-directional removal sync verified in Hospital and User models'
    );

    console.log('\n--- SECTION 5: AUDIT LOG GENERATION ---');

    // TEST 21: Verify AuditLog records exist for admin operations
    const auditLogs = await AuditLog.find({ action: { $in: ['COORDINATOR_ASSIGNED', 'COORDINATOR_REMOVED', 'DONATION_VERIFIED'] } });
    assert(auditLogs.length >= 2, 'TEST 21: Audit logs successfully recorded for operational actions');

  } catch (err) {
    console.error('Test Suite Exception:', err);
    assert(false, `Test Suite crashed: ${err.message}`);
  }

  console.log('\n====================================================');
  console.log('  PHASE 4 ADMIN & COORDINATOR SUITE SUMMARY');
  console.log('====================================================');
  const passed = testResults.filter(r => r.pass).length;
  const failed = testResults.filter(r => !r.pass).length;
  console.log(`TOTAL: ${testResults.length} | PASSED: ${passed} | FAILED: ${failed}`);
  console.log('====================================================');

  if (failed === 0) {
    console.log('  🎉 ALL PHASE 4 TESTS PASSED (100%)');
  } else {
    console.log('  ❌ SOME TESTS FAILED');
  }
  console.log('====================================================\n');

  process.exit(failed === 0 ? 0 : 1);
}

runPhase4Tests();
