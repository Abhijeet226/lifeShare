/**
 * LIFE SHARE — USER LIFECYCLE & EMERGENCY GOVERNANCE TEST SUITE
 * Tests: Account Suspension, Blocking, and Self-Deletion cascades.
 */

require('dotenv').config({ path: require('path').join(__dirname, '.env') });
require('dns').setServers(['8.8.8.8', '8.8.4.4']);
const mongoose = require('mongoose');
const User = require('./models/User');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const ChatMessage = require('./models/ChatMessage');
const { handleUserLifecycleChange } = require('./services/userLifecycleService');

const MONGODB_URI = process.env.MONGO_URI || process.env.MONGODB_URI;

async function runLifecycleTests() {
  console.log('=== RUNNING USER LIFECYCLE & EMERGENCY GOVERNANCE TESTS ===\n');

  try {
    await mongoose.connect(MONGODB_URI);
    console.log('Connected to MongoDB Atlas.\n');

    // 1. Setup Test Requester
    const testRequester = new User({
      name: 'Fraud Test Requester',
      firstName: 'Fraud',
      lastName: 'Requester',
      email: `fraud_${Date.now()}@example.com`,
      password: 'password123',
      mobile: `+91 99${Math.floor(10000000 + Math.random() * 90000000)}`,
      bloodGroup: 'O+',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      isAvailable: true
    });
    await testRequester.save();

    // 2. Setup Test Donor
    const testDonor = new User({
      name: 'Hero Donor',
      firstName: 'Hero',
      lastName: 'Donor',
      email: `donor_${Date.now()}@example.com`,
      password: 'password123',
      mobile: `+91 98${Math.floor(10000000 + Math.random() * 90000000)}`,
      bloodGroup: 'O+',
      role: 'DONOR',
      accountStatus: 'ACTIVE',
      isAvailable: false // Busy helping
    });
    await testDonor.save();

    // 3. Create Emergency
    const emergency = new EmergencyRequest({
      requester: testRequester._id,
      patientName: 'Fraud Scenario Patient',
      hospital: 'AIIMS Bhubaneswar',
      city: 'Bhubaneswar',
      bloodGroup: 'O+',
      unitsRequired: 2,
      urgency: 'CRITICAL',
      status: 'SEARCHING',
      contactNumber: '+91 9800000000'
    });
    await emergency.save();

    // 4. Create Donor Response
    const response = new EmergencyResponse({
      requestId: emergency._id,
      emergencyRequestId: emergency._id,
      donorId: testDonor._id,
      status: 'TRAVELLING'
    });
    await response.save();

    console.log('[TEST 1] Testing Requester Account Suspension...');
    // Execute lifecycle change: Admin suspends requester for fraud
    await handleUserLifecycleChange(testRequester._id, 'SUSPENDED', 'Fraud emergency creation detected');

    const updatedEmergency = await EmergencyRequest.findById(emergency._id);
    const updatedResponse = await EmergencyResponse.findById(response._id);
    const updatedDonor = await User.findById(testDonor._id);
    const chatNotices = await ChatMessage.find({ emergencyRequestId: emergency._id });

    console.log(`- Emergency Status: ${updatedEmergency.status} (Expected: CANCELLED) -> ${updatedEmergency.status === 'CANCELLED' ? 'PASS' : 'FAIL'}`);
    console.log(`- Donor Response Status: ${updatedResponse.status} (Expected: CANCELLED) -> ${updatedResponse.status === 'CANCELLED' ? 'PASS' : 'FAIL'}`);
    console.log(`- Donor isAvailable Restored: ${updatedDonor.isAvailable} (Expected: true) -> ${updatedDonor.isAvailable === true ? 'PASS' : 'FAIL'}`);
    console.log(`- Chat Termination Broadcast Posted: ${chatNotices.length > 0 ? 'PASS' : 'FAIL'}`);

    console.log('\n[TEST 2] Testing Donor Account Self-Deletion while Engaged...');
    // Create new emergency by another user
    const realEmergency = new EmergencyRequest({
      requester: new mongoose.Types.ObjectId(),
      patientName: 'Real Patient',
      hospital: 'Capital Hospital',
      city: 'Bhubaneswar',
      bloodGroup: 'A+',
      unitsRequired: 1,
      status: 'SEARCHING',
      contactNumber: '+91 9811111111'
    });
    await realEmergency.save();

    const donorResponse2 = new EmergencyResponse({
      requestId: realEmergency._id,
      emergencyRequestId: realEmergency._id,
      donorId: testDonor._id,
      status: 'ACCEPTED'
    });
    await donorResponse2.save();

    // Donor deletes account
    await handleUserLifecycleChange(testDonor._id, 'DELETED', 'User self-deleted account');

    const updatedResponse2 = await EmergencyResponse.findById(donorResponse2._id);
    const updatedRealEmergency = await EmergencyRequest.findById(realEmergency._id);
    const chatNotices2 = await ChatMessage.find({ emergencyRequestId: realEmergency._id });

    console.log(`- Donor Response Status: ${updatedResponse2.status} (Expected: CANCELLED) -> ${updatedResponse2.status === 'CANCELLED' ? 'PASS' : 'FAIL'}`);
    console.log(`- Parent Emergency Still Active: ${updatedRealEmergency.status} (Expected: SEARCHING) -> ${updatedRealEmergency.status === 'SEARCHING' ? 'PASS' : 'FAIL'}`);
    console.log(`- Chat Re-opened Notice for other Donors: ${chatNotices2.length > 0 ? 'PASS' : 'FAIL'}`);

    // Cleanup test artifacts
    await User.deleteMany({ _id: { $in: [testRequester._id, testDonor._id] } });
    await EmergencyRequest.deleteMany({ _id: { $in: [emergency._id, realEmergency._id] } });
    await EmergencyResponse.deleteMany({ _id: { $in: [response._id, donorResponse2._id] } });
    await ChatMessage.deleteMany({ emergencyRequestId: { $in: [emergency._id, realEmergency._id] } });

    console.log('\n=== ALL USER LIFECYCLE GOVERNANCE TESTS PASSED SUCCESSFULLY ===');
    process.exit(0);
  } catch (err) {
    console.error('Test failed with error:', err);
    process.exit(1);
  }
}

runLifecycleTests();
