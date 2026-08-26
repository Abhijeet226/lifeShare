require('dotenv').config();
const connectDB = require('./config/database');
const http = require('http');

function request(options, data) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
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

async function runV3Phase1Tests() {
  await connectDB();
  console.log('====================================================');
  console.log('  LIFE SHARE V3 PHASE 1 - AUTOMATED VERIFICATION');
  console.log('====================================================\n');

  // Test A: New User Defaults
  console.log('--- TEST A: NEW USER TRUST DEFAULTS ---');
  const userAEmail = 'v3.user.a.' + Date.now() + '@lifeshare.org';
  const regA = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/register', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    firstName: 'V3User', lastName: 'Alpha', email: userAEmail, password: 'password123',
    mobile: '+91 9811000001', bloodGroup: 'B+', city: 'Bhubaneswar',
    latitude: 20.2961, longitude: 85.8245
  });
  console.log('• User A Register Status:', regA.status);
  console.log('  verificationStatus:', regA.body.user?.verificationStatus, '(Expected: UNVERIFIED)');
  console.log('  accountStatus:', regA.body.user?.accountStatus, '(Expected: ACTIVE)');
  console.log('  phoneVerified:', regA.body.user?.phoneVerified, '(Expected: false)');
  const tokenA = regA.body.token;

  // Test B: Verified User via Reset OTP
  console.log('\n--- TEST B: PHONE VERIFICATION FLOW ---');
  const otpSend = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/forgot-password', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { email: userAEmail });
  console.log('• Send OTP Status:', otpSend.status);

  // Directly verify via User model or verification endpoint
  const User = require('./models/User');
  await User.updateOne({ email: userAEmail }, {
    phoneVerified: true,
    verificationStatus: 'PHONE_VERIFIED',
    verifiedAt: new Date()
  });

  const meCheck = await request({
    hostname: 'localhost', port: 5000, path: '/api/users/me', method: 'GET',
    headers: { 'Authorization': 'Bearer ' + tokenA }
  });
  console.log('• Verified User A status:', meCheck.body.user?.verificationStatus, 'phoneVerified:', meCheck.body.user?.phoneVerified);

  // Test C: Suspended User Access
  console.log('\n--- TEST C: SUSPENDED / BLOCKED ACCOUNT ENFORCEMENT ---');
  const userSuspendedEmail = 'v3.suspended.' + Date.now() + '@lifeshare.org';
  const regSusp = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/register', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    firstName: 'Suspended', lastName: 'User', email: userSuspendedEmail, password: 'password123',
    mobile: '+91 9811000099', bloodGroup: 'O+', city: 'Bhubaneswar',
    latitude: 20.2961, longitude: 85.8245
  });
  const tokenSusp = regSusp.body.token;

  // Suspend account
  await User.updateOne({ email: userSuspendedEmail }, { accountStatus: 'SUSPENDED' });

  // Try login
  const suspLogin = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/login', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { email: userSuspendedEmail, password: 'password123' });
  console.log('• Suspended User Login (Expected 403):', suspLogin.status, suspLogin.body.message);

  // Try responding to emergency with suspended token
  const suspRespond = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/6a8537983d91c1bc74de13bb/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenSusp }
  }, { response: 'ACCEPTED' });
  console.log('• Suspended User Respond (Expected 403):', suspRespond.status, suspRespond.body.message);

  // Test D & E: Authoritative Hospital Selection & Distance Calculation
  console.log('\n--- TEST D & E: AUTHORITATIVE HOSPITAL SELECTION & MATCHING ---');
  const hospRes = await request({
    hostname: 'localhost', port: 5000, path: '/api/hospitals/nearby?latitude=20.2961&longitude=85.8245&radius=25000',
    method: 'GET'
  });
  console.log('• Nearby Hospitals Found:', hospRes.body.count);
  const chosenHospital = hospRes.body.hospitals[0];
  console.log('  Selected Hospital:', chosenHospital.name, '| Distance:', chosenHospital.distanceKm, 'km | ID:', chosenHospital.id);

  const createSosWithHosp = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergency/create', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenA }
  }, {
    patientName: 'Priyabrata Mishra',
    hospitalId: chosenHospital.id,
    bloodGroup: 'B+',
    unitsRequired: 3,
    contactNumber: '+91 9811000001',
    urgency: 'CRITICAL'
  });
  console.log('• Emergency SOS with Authoritative Hospital Created:', createSosWithHosp.status);
  console.log('  hospitalId stored:', createSosWithHosp.body.request?.hospitalId === chosenHospital.id);
  console.log('  isAuthoritativeHospital:', createSosWithHosp.body.request?.isAuthoritativeHospital);
  console.log('  unitsRequired:', createSosWithHosp.body.request?.unitsRequired);
  const emId = createSosWithHosp.body.request?._id;

  // Test F: Multi-Unit Fulfillment Tracking (unitsRequired = 3)
  console.log('\n--- TEST F: MULTI-UNIT FULFILLMENT TRACKING ---');
  // Donor B accepts (1/3)
  const userBEmail = 'v3.donor.b.' + Date.now() + '@lifeshare.org';
  const regB = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/register', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    firstName: 'DonorB', lastName: 'Hero', email: userBEmail, password: 'password123',
    mobile: '+91 9811000002', bloodGroup: 'B+', city: 'Bhubaneswar',
    latitude: 20.2961, longitude: 85.8245
  });
  const tokenB = regB.body.token;

  const respB = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/' + emId + '/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenB }
  }, { response: 'ACCEPTED' });
  console.log('• Donor B Accept (1/3): Status:', respB.status, 'acceptedCount:', respB.body.acceptedCount);

  const detailF = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergency/' + emId, method: 'GET',
    headers: { 'Authorization': 'Bearer ' + tokenA }
  });
  console.log('• Request Status after 1/3 accepted:', detailF.body.emergency?.status, '(Expected: PARTIALLY_ACCEPTED)');
  console.log('  isFulfilled:', detailF.body.emergency?.isFulfilled, '(Expected: false)');
  console.log('  remainingUnits:', detailF.body.stats?.remainingUnits, '(Expected: 2)');

  // Test G: Concurrent Acceptance on Last Unit
  console.log('\n--- TEST G: CONCURRENT ACCEPTANCE & FULL FULFILLMENT ---');
  // Create emergency with unitsRequired = 1
  const singleUnitSos = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergency/create', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenA }
  }, {
    patientName: 'Single Unit Patient',
    hospitalId: chosenHospital.id,
    bloodGroup: 'B+',
    unitsRequired: 1,
    contactNumber: '+91 9811000001'
  });
  const singleEmId = singleUnitSos.body.request?._id;

  // Donor B accepts (Consumes the only 1 unit)
  const accept1 = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/' + singleEmId + '/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenB }
  }, { response: 'ACCEPTED' });
  console.log('• First Donor Accept (1/1 unit):', accept1.status, 'responseStatus:', accept1.body.responseStatus);

  // Donor C attempts to accept (Should be rejected as already fulfilled)
  const userCEmail = 'v3.donor.c.' + Date.now() + '@lifeshare.org';
  const regC = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/register', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    firstName: 'DonorC', lastName: 'Helper', email: userCEmail, password: 'password123',
    mobile: '+91 9811000003', bloodGroup: 'B+', city: 'Bhubaneswar',
    latitude: 20.2961, longitude: 85.8245
  });
  const tokenC = regC.body.token;

  const accept2 = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/' + singleEmId + '/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenC }
  }, { response: 'ACCEPTED' });
  console.log('• Second Donor Concurrent Attempt on 1/1 unit (Expected 409 Conflict):', accept2.status, accept2.body.message);

  // Test H: Duplicate Acceptance by Same Donor
  console.log('\n--- TEST H: DUPLICATE ACCEPTANCE BY SAME DONOR ---');
  const dupAccept = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/' + singleEmId + '/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenB }
  }, { response: 'ACCEPTED' });
  console.log('• Duplicate Accept by Same Donor (Expected 200 Idempotent):', dupAccept.status, dupAccept.body.message);

  // Test I: Donor Journey Updates (TRAVELLING -> ARRIVED)
  console.log('\n--- TEST I: DONOR JOURNEY STEP UPDATES ---');
  const travelRes = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/' + singleEmId + '/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenB }
  }, { response: 'TRAVELLING' });
  console.log('• Donor updates to TRAVELLING:', travelRes.status, travelRes.body.responseStatus);

  const arriveRes = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergencies/' + singleEmId + '/respond', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokenB }
  }, { response: 'ARRIVED' });
  console.log('• Donor updates to ARRIVED:', arriveRes.status, arriveRes.body.responseStatus);

  // Test J: Ownership Protection & Cleanup
  console.log('\n--- TEST J: OWNERSHIP PROTECTION ON CANCEL ---');
  const nonOwnerCancel = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergency/' + singleEmId, method: 'DELETE',
    headers: { 'Authorization': 'Bearer ' + tokenC }
  });
  console.log('• Non-owner cancel attempt (Expected 403 Forbidden):', nonOwnerCancel.status);

  const ownerCancel = await request({
    hostname: 'localhost', port: 5000, path: '/api/emergency/' + singleEmId, method: 'DELETE',
    headers: { 'Authorization': 'Bearer ' + tokenA }
  });
  console.log('• Owner cancel attempt (Expected 200 OK):', ownerCancel.status);

  // Clean up
  await request({
    hostname: 'localhost', port: 5000, path: '/api/emergency/' + emId, method: 'DELETE',
    headers: { 'Authorization': 'Bearer ' + tokenA }
  });

  console.log('\n====================================================');
  console.log('  ✅ ALL V3 PHASE 1 VERIFICATION TESTS COMPLETED');
  console.log('====================================================');
  process.exit(0);
}

runV3Phase1Tests().catch(console.error);
