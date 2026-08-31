require('dotenv').config({ path: require('path').join(__dirname, '.env') });
require('dns').setServers(['8.8.8.8', '8.8.4.4']);
const mongoose = require('mongoose');
const User = require('./models/User');
const Notification = require('./models/Notification');
const notificationService = require('./services/notificationService');

async function runTest() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log('✅ Connected to MongoDB Atlas');

  // 1. Get or create a test user
  let testUser = await User.findOne({ email: 'donor_test_notif@lifeshare.test' });
  if (!testUser) {
    testUser = await User.create({
      name: 'Notification Test Donor',
      email: 'donor_test_notif@lifeshare.test',
      mobile: '+919999988888',
      password: 'HashedPassword123!',
      bloodGroup: 'O+',
      city: 'Bhubaneswar',
      accountStatus: 'ACTIVE',
      isAvailable: true
    });
  }

  console.log('User ID:', testUser._id);

  // Clean old test notifications
  await Notification.deleteMany({ userId: testUser._id });

  // 2. Dispatch Emergency Notification (Initial SOS)
  const dummyRequestId = new mongoose.Types.ObjectId().toString();
  await notificationService.sendEmergencyNotification(testUser._id, {
    _id: dummyRequestId,
    bloodGroup: 'O+',
    hospital: 'AIIMS Bhubaneswar',
    unitsRequired: 2,
    urgency: 'CRITICAL'
  });

  let notifs = await Notification.find({ userId: testUser._id });
  console.log('After SOS dispatch, notifs count:', notifs.length);
  console.log('Title:', notifs[0].title, '| Channel:', notifs[0].channel, '| CollapseKey:', notifs[0].collapseKey);

  // 3. Test In-Place Collapsing: Status updates to DONOR_ACCEPTED
  await notificationService.sendToUser(testUser._id, {
    title: '🩸 Donor On The Way!',
    body: 'Volunteer donor Rahul S. is in transit (ETA ~15 mins)',
    notificationType: 'DONOR_ACCEPTED',
    collapseKey: `emergency_${dummyRequestId}`,
    data: {
      requestId: dummyRequestId,
      status: 'TRAVELLING'
    }
  });

  notifs = await Notification.find({ userId: testUser._id });
  console.log('After in-place update, notifs count (MUST BE 1):', notifs.length);
  console.log('Updated Title:', notifs[0].title, '| Updated Status:', notifs[0].status);

  // 4. Dispatch a Certificate notification
  await notificationService.sendToUser(testUser._id, {
    title: '🎉 Blood Donation Verified!',
    body: 'Your donation certificate is ready. 100 Karma points awarded.',
    notificationType: 'DONATION_VERIFIED',
    data: {
      certificateId: 'CERT-2026-TEST'
    }
  });

  notifs = await Notification.find({ userId: testUser._id });
  console.log('Total notifs (Emergency + Certificate):', notifs.length);

  // 5. Verify Unread Counts
  const unreadEmergency = await Notification.countDocuments({ userId: testUser._id, channel: 'EMERGENCY', isRead: false });
  const unreadCertificates = await Notification.countDocuments({ userId: testUser._id, channel: 'CERTIFICATES', isRead: false });
  console.log(`Unread Channels -> EMERGENCY: ${unreadEmergency}, CERTIFICATES: ${unreadCertificates}`);

  // Cleanup test user
  await Notification.deleteMany({ userId: testUser._id });
  await User.deleteOne({ _id: testUser._id });
  console.log('🎉 ALL NOTIFICATION TESTS PASSED SUCCESSFULLY!');
  process.exit(0);
}

runTest().catch((e) => {
  console.error('❌ Test failed:', e);
  process.exit(1);
});
