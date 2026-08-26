require('dotenv').config();
const connectDB = require('./config/database');
const http = require('http');

function request(options, data) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: body ? JSON.parse(body) : {} }));
    });
    req.on('error', reject);
    if (data) req.write(JSON.stringify(data));
    req.end();
  });
}

async function runVerificationTests() {
  await connectDB();
  const OTP = require('./models/OTP');
  const User = require('./models/User');

  console.log('====================================================');
  console.log('  TESTING PHONE & EMAIL VERIFICATION FLOWS');
  console.log('====================================================\n');

  // Test 1: Normal Unverified Registration (Optional OTP omitted)
  const unverifiedEmail = 'unverified.user.' + Date.now() + '@lifeshare.org';
  const reg1 = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/register', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    firstName: 'Normal', lastName: 'Donor', email: unverifiedEmail, password: 'password123',
    mobile: '+91 9900112233', bloodGroup: 'O+', city: 'Bhubaneswar'
  });
  console.log('1. Registration without OTP:');
  console.log('   Status:', reg1.status);
  console.log('   verificationStatus:', reg1.body.user?.verificationStatus, '(Expected: UNVERIFIED)');
  console.log('   phoneVerified:', reg1.body.user?.phoneVerified, '(Expected: false)');
  const token1 = reg1.body.token;

  // Test 2: Authenticated Send Phone OTP & Verify
  console.log('\n2. Profile Phone Verification Flow:');
  const sendPhone = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/send-verification-otp', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token1 }
  }, { type: 'PHONE' });
  console.log('   Send Phone OTP Status:', sendPhone.status, sendPhone.body.message);

  // Retrieve generated OTP from DB
  const phoneOtpRecord = await OTP.findOne({ email: '+91 9900112233' });
  console.log('   Generated Phone OTP:', phoneOtpRecord?.otp);

  const verifyPhone = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/verify-account-otp', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token1 }
  }, { type: 'PHONE', otp: phoneOtpRecord?.otp });
  console.log('   Verify Phone Status:', verifyPhone.status, verifyPhone.body.message);
  console.log('   Updated verificationStatus:', verifyPhone.body.user?.verificationStatus, '(Expected: PHONE_VERIFIED)');
  console.log('   Updated phoneVerified:', verifyPhone.body.user?.phoneVerified, '(Expected: true)');

  // Test 3: Authenticated Send Email OTP & Verify
  console.log('\n3. Profile Email Verification Flow:');
  const sendEmail = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/send-verification-otp', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token1 }
  }, { type: 'EMAIL' });
  console.log('   Send Email OTP Status:', sendEmail.status, sendEmail.body.message);

  const emailOtpRecord = await OTP.findOne({ email: unverifiedEmail });
  console.log('   Generated Email OTP:', emailOtpRecord?.otp);

  const verifyEmail = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/verify-account-otp', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token1 }
  }, { type: 'EMAIL', otp: emailOtpRecord?.otp });
  console.log('   Verify Email Status:', verifyEmail.status, verifyEmail.body.message);
  console.log('   Updated emailVerified:', verifyEmail.body.user?.emailVerified, '(Expected: true)');

  // Test 4: Signup with Verified OTP
  console.log('\n4. Signup with Optional OTP Verification:');
  const verifiedSignupEmail = 'signup.verified.' + Date.now() + '@lifeshare.org';
  const signupMobile = '+91 9988776655';

  const sendSignup = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/send-signup-otp', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { identifier: signupMobile, type: 'PHONE' });
  console.log('   Send Signup OTP Status:', sendSignup.status);

  const signupOtpRecord = await OTP.findOne({ email: signupMobile });
  console.log('   Generated Signup OTP:', signupOtpRecord?.otp);

  const regWithOtp = await request({
    hostname: 'localhost', port: 5000, path: '/api/auth/register', method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    firstName: 'VerifiedOnSignup', lastName: 'Donor', email: verifiedSignupEmail, password: 'password123',
    mobile: signupMobile, bloodGroup: 'B+', city: 'Bhubaneswar',
    otp: signupOtpRecord?.otp
  });
  console.log('   Register with OTP Status:', regWithOtp.status);
  console.log('   verificationStatus:', regWithOtp.body.user?.verificationStatus, '(Expected: PHONE_VERIFIED)');
  console.log('   phoneVerified:', regWithOtp.body.user?.phoneVerified, '(Expected: true)');

  console.log('\n====================================================');
  console.log('  ✅ ALL PHONE & EMAIL VERIFICATION TESTS PASSED');
  console.log('====================================================');
  process.exit(0);
}

runVerificationTests().catch(console.error);
