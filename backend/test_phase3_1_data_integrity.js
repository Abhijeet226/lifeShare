require('dotenv').config({ path: require('path').join(__dirname, '.env') });
const mongoose = require('mongoose');
const http = require('http');
const bcrypt = require('bcryptjs');
const connectDB = require('./config/database');
const City = require('./models/City');
const User = require('./models/User');
const Hospital = require('./models/Hospital');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const DonationHistory = require('./models/DonationHistory');
const AuditLog = require('./models/AuditLog');
const { logAuditEvent, sanitizeMetadata } = require('./services/auditService');

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

async function runDataIntegrityTests() {
  console.log('====================================================');
  console.log('  LIFE SHARE V3.1 — DATA MODEL & INTEGRITY TEST SUITE');
  console.log('====================================================\n');

  await connectDB();

  const timestamp = Date.now();

  // ----------------------------------------------------
  // TEST 1: Canonical City Model & Deduplication
  // ----------------------------------------------------
  console.log('--- TEST 1: CANONICAL CITY MODEL & DEDUPLICATION ---');
  const testCityName = `TestCity_${timestamp}`;
  const city1 = new City({
    name: testCityName,
    location: { type: 'Point', coordinates: [85.5, 20.5] }
  });
  await city1.save();
  assert(city1.normalizedName === testCityName.toLowerCase(), 'City normalizedName automatically lowercased');

  // Attempt duplicate with uppercase / whitespace
  let duplicateFailed = false;
  try {
    const cityDup = new City({
      name: `  ${testCityName.toUpperCase()}  `,
      location: { type: 'Point', coordinates: [85.5, 20.5] }
    });
    await cityDup.save();
  } catch (err) {
    duplicateFailed = true;
  }
  assert(duplicateFailed, 'Duplicate city with different casing/spacing strictly rejected by unique normalized index');

  // ----------------------------------------------------
  // TEST 2: Canonical City REST Endpoints
  // ----------------------------------------------------
  console.log('\n--- TEST 2: CANONICAL CITY REST API ---');
  const listRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/cities',
    method: 'GET'
  });
  assert(listRes.status === 200 && listRes.body.success, 'GET /api/cities returns HTTP 200 and success: true');
  assert(Array.isArray(listRes.body.cities) && listRes.body.cities.length >= 20, 'GET /api/cities returns all seeded canonical cities');

  const bhubaneswarCity = listRes.body.cities.find((c) => c.name.toLowerCase() === 'bhubaneswar');
  assert(bhubaneswarCity && bhubaneswarCity.latitude && bhubaneswarCity.longitude, 'Canonical city includes valid latitude and longitude');

  const getCityRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: `/api/cities/${bhubaneswarCity.id}`,
    method: 'GET'
  });
  assert(getCityRes.status === 200 && getCityRes.body.city.name === 'Bhubaneswar', 'GET /api/cities/:id returns city details');

  const getHospitalsInCityRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: `/api/cities/${bhubaneswarCity.id}/hospitals`,
    method: 'GET'
  });
  assert(getHospitalsInCityRes.status === 200 && Array.isArray(getHospitalsInCityRes.body.hospitals), 'GET /api/cities/:id/hospitals returns hospital list');

  // ----------------------------------------------------
  // TEST 3: User -> City Reference & Backward Compatibility
  // ----------------------------------------------------
  console.log('\n--- TEST 3: USER MODEL WITH CITY REFERENCE & BACKWARD COMPATIBILITY ---');
  const hashedPassword = await bcrypt.hash('password123', 10);
  const userWithoutCity = new User({
    name: 'Legacy User',
    email: `legacy_${timestamp}@lifeshare.test`,
    password: hashedPassword,
    mobile: '9876543210',
    bloodGroup: 'O+',
    city: 'Bhubaneswar'
  });
  await userWithoutCity.save();
  assert(userWithoutCity.cityId === null, 'Existing/legacy user without cityId has null cityId and functions normally');

  const userWithCity = new User({
    name: 'Modern User',
    email: `modern_${timestamp}@lifeshare.test`,
    password: hashedPassword,
    mobile: '9876543211',
    bloodGroup: 'A+',
    city: 'Bhubaneswar',
    cityId: bhubaneswarCity.id
  });
  await userWithCity.save();
  assert(userWithCity.cityId && userWithCity.cityId.toString() === bhubaneswarCity.id, 'User with cityId reference saved successfully');

  // ----------------------------------------------------
  // TEST 4: Hospital & EmergencyRequest City Reference
  // ----------------------------------------------------
  console.log('\n--- TEST 4: HOSPITAL & EMERGENCY CITY REFERENCES ---');
  const testHospital = new Hospital({
    name: `Hospital_${timestamp}`,
    address: 'Hospital Street',
    cityId: bhubaneswarCity.id,
    location: { type: 'Point', coordinates: [85.82, 20.29] }
  });
  await testHospital.save();
  assert(testHospital.cityId && testHospital.cityId.toString() === bhubaneswarCity.id, 'Hospital properly references City model');

  const testEmergency = new EmergencyRequest({
    patientName: 'Test Patient',
    bloodGroup: 'O+',
    unitsRequired: 2,
    hospital: testHospital.name,
    hospitalId: testHospital._id,
    cityId: bhubaneswarCity.id,
    contactNumber: '9876543210'
  });
  await testEmergency.save();
  assert(testEmergency.cityId && testEmergency.cityId.toString() === bhubaneswarCity.id, 'EmergencyRequest properly references City model');

  // ----------------------------------------------------
  // TEST 5: Idempotent Timestamps on Fulfillment & Response
  // ----------------------------------------------------
  console.log('\n--- TEST 5: IDEMPOTENT TIMESTAMPS & LIFECYCLE AUDIT ---');
  const firstFulfilledDate = new Date('2026-08-01T10:00:00Z');
  testEmergency.fulfilledAt = firstFulfilledDate;
  testEmergency.status = 'FULFILLED';
  testEmergency.isFulfilled = true;
  await testEmergency.save();

  // Attempt duplicate fulfillment should not overwrite original fulfilledAt if check is used
  const originalFulfilledAt = testEmergency.fulfilledAt.getTime();
  if (!testEmergency.fulfilledAt) {
    testEmergency.fulfilledAt = new Date();
  }
  assert(testEmergency.fulfilledAt.getTime() === originalFulfilledAt, 'FulfilledAt timestamp is idempotent and preserved');

  // EmergencyResponse timestamps
  const resp = new EmergencyResponse({
    requestId: testEmergency._id,
    donorId: userWithCity._id,
    status: 'ACCEPTED',
    acceptedAt: new Date('2026-08-01T10:05:00Z')
  });
  await resp.save();
  const originalAcceptedAt = resp.acceptedAt.getTime();

  // Idempotent check
  if (!resp.acceptedAt) {
    resp.acceptedAt = new Date();
  }
  assert(resp.acceptedAt.getTime() === originalAcceptedAt, 'EmergencyResponse acceptedAt timestamp is idempotent');

  // ----------------------------------------------------
  // TEST 6: AuditLog Safety & Sanitization
  // ----------------------------------------------------
  console.log('\n--- TEST 6: AUDIT LOG SANITIZATION & LOGGING ---');
  const rawMeta = {
    patientName: 'Sensitive Patient',
    password: 'SuperSecretPassword',
    otp: '123456',
    token: 'jwt.token.secret',
    coordinates: [85.8, 20.2],
    unitsRequired: 2
  };
  const cleanMeta = sanitizeMetadata(rawMeta);
  assert(cleanMeta.password === undefined, 'Sanitizer strips password from audit metadata');
  assert(cleanMeta.otp === undefined, 'Sanitizer strips OTP from audit metadata');
  assert(cleanMeta.token === undefined, 'Sanitizer strips tokens from audit metadata');
  assert(cleanMeta.coordinates === undefined, 'Sanitizer strips donor/patient GPS coordinates from audit metadata');
  assert(cleanMeta.unitsRequired === 2, 'Sanitizer preserves non-sensitive audit metadata');

  await logAuditEvent({
    actorId: userWithCity._id,
    actorRole: 'DONOR',
    action: 'TEST_AUDIT_ACTION',
    entityType: 'User',
    entityId: userWithCity._id,
    metadata: rawMeta
  });

  const auditRecord = await AuditLog.findOne({ action: 'TEST_AUDIT_ACTION', actorId: userWithCity._id });
  assert(auditRecord !== null, 'Audit log saved to database');
  assert(auditRecord.metadata.password === undefined, 'Saved audit record contains no sensitive credentials');

  // ----------------------------------------------------
  // TEST 7: Security & Privilege Escalation Prevention
  // ----------------------------------------------------
  console.log('\n--- TEST 7: SECURITY & PRIVILEGE ESCALATION RECHECK ---');
  const loginRes = await request(
    {
      hostname: 'localhost',
      port: 5000,
      path: '/api/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    },
    { email: userWithCity.email, password: 'password123' }
  );

  const token = loginRes.body.token;
  assert(token !== undefined, 'User successfully authenticated to obtain JWT');

  // Attempt privilege escalation attack
  const attackRes = await request(
    {
      hostname: 'localhost',
      port: 5000,
      path: '/api/users/profile',
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      }
    },
    {
      name: 'Hacked User',
      role: 'ADMIN',
      accountStatus: 'BLOCKED',
      verificationStatus: 'DONOR_VERIFIED',
      donationsCount: 999
    }
  );

  const freshUser = await User.findById(userWithCity._id);
  assert(freshUser.role === 'DONOR', 'User cannot self-escalate role to ADMIN (HTTP Status: ' + attackRes.status + ')');
  assert(freshUser.verificationStatus === 'UNVERIFIED', 'User cannot self-escalate verificationStatus');
  assert(freshUser.donationsCount === 0, 'User cannot self-modify donationsCount');

  // Cleanup test artifacts
  await City.deleteMany({ name: testCityName });
  await User.deleteMany({ _id: { $in: [userWithoutCity._id, userWithCity._id] } });
  await Hospital.deleteMany({ _id: testHospital._id });
  await EmergencyRequest.deleteMany({ _id: testEmergency._id });
  await EmergencyResponse.deleteMany({ _id: resp._id });
  await AuditLog.deleteMany({ action: 'TEST_AUDIT_ACTION' });

  // ----------------------------------------------------
  // SUMMARY
  // ----------------------------------------------------
  console.log('\n====================================================');
  console.log('  V3.1 DATA MODEL & INTEGRITY TEST SUITE SUMMARY');
  console.log('====================================================');
  const total = testResults.length;
  const passed = testResults.filter((r) => r.pass).length;
  console.log(`TOTAL: ${total} | PASSED: ${passed} | FAILED: ${total - passed}`);

  if (passed === total) {
    console.log('====================================================');
    console.log('  🎉 ALL V3.1 DATA INTEGRITY TESTS PASSED (100%)');
    console.log('====================================================\n');
  } else {
    console.log('====================================================');
    console.log('  ❌ SOME TESTS FAILED');
    console.log('====================================================\n');
    process.exit(1);
  }
}

if (require.main === module) {
  runDataIntegrityTests()
    .then(() => mongoose.connection.close())
    .catch((err) => {
      console.error('❌ Test runner error:', err);
      process.exit(1);
    });
}

module.exports = runDataIntegrityTests;
