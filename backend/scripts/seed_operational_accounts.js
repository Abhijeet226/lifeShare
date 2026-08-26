/**
 * Seed Admin and Coordinator Operational Accounts
 */

require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const connectDB = require('../config/database');
const User = require('../models/User');
const Hospital = require('../models/Hospital');
const City = require('../models/City');

async function seedOperationalAccounts() {
  console.log('Connecting to database...');
  await connectDB();

  const city = await City.findOne({ normalizedName: 'bhubaneswar' }) ||
    await new City({
      name: 'Bhubaneswar',
      stateName: 'Odisha',
      location: { type: 'Point', coordinates: [85.8245, 20.2961] }
    }).save();

  // Find or create AIIMS Bhubaneswar
  let aiims = await Hospital.findOne({ name: /AIIMS/i });
  if (!aiims) {
    aiims = await new Hospital({
      name: 'AIIMS Hospital Bhubaneswar',
      address: 'Sijua, Patrapada, Bhubaneswar, Odisha 751019',
      cityId: city._id,
      location: { type: 'Point', coordinates: [85.7766, 20.2289] },
      phone: '+91 674 247 6789',
      verified: true,
      isVerified: true,
      emergencySupport: true,
      authorizedCoordinatorIds: []
    }).save();
  }

  const sharedPassword = 'Password@123';
  const hashedPassword = await bcrypt.hash(sharedPassword, 10);

  // 1. Create or Update Super Admin
  const adminEmail = 'admin@lifeshare.in';
  let admin = await User.findOne({ email: adminEmail });
  if (!admin) {
    admin = new User({
      name: 'LifeShare Super Admin',
      firstName: 'LifeShare',
      lastName: 'Admin',
      email: adminEmail,
      password: hashedPassword,
      mobile: '+91 9800000001',
      bloodGroup: 'O+',
      role: 'ADMIN',
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      phoneVerified: true,
      emailVerified: true,
      cityId: city._id,
      isAvailable: true
    });
  } else {
    admin.password = hashedPassword;
    admin.role = 'ADMIN';
    admin.accountStatus = 'ACTIVE';
    admin.verificationStatus = 'DONOR_VERIFIED';
    admin.phoneVerified = true;
    admin.emailVerified = true;
  }
  await admin.save();
  console.log(`✅ Admin account configured: ${adminEmail}`);

  // 2. Create or Update Hospital Coordinator
  const coordEmail = 'coordinator.aiims@lifeshare.in';
  let coordinator = await User.findOne({ email: coordEmail });
  if (!coordinator) {
    coordinator = new User({
      name: 'AIIMS Hospital Coordinator',
      firstName: 'AIIMS',
      lastName: 'Coordinator',
      email: coordEmail,
      password: hashedPassword,
      mobile: '+91 9800000002',
      bloodGroup: 'O+',
      role: 'COORDINATOR',
      hospitalId: aiims._id,
      accountStatus: 'ACTIVE',
      verificationStatus: 'DONOR_VERIFIED',
      phoneVerified: true,
      emailVerified: true,
      cityId: city._id,
      isAvailable: true
    });
  } else {
    coordinator.password = hashedPassword;
    coordinator.role = 'COORDINATOR';
    coordinator.hospitalId = aiims._id;
    coordinator.accountStatus = 'ACTIVE';
    coordinator.verificationStatus = 'DONOR_VERIFIED';
    coordinator.phoneVerified = true;
    coordinator.emailVerified = true;
  }
  await coordinator.save();

  // Bi-directional link on Hospital
  await Hospital.findByIdAndUpdate(aiims._id, {
    $addToSet: { authorizedCoordinatorIds: coordinator._id }
  });
  console.log(`✅ Coordinator account configured: ${coordEmail} (Assigned to ${aiims.name})`);

  console.log('\n======================================================');
  console.log('  OPERATIONAL ACCOUNTS SEEDED SUCCESSFULLY');
  console.log('======================================================');
  console.log(`🔑 Shared Password:   ${sharedPassword}`);
  console.log(`👤 Admin Email:       ${adminEmail}`);
  console.log(`🏥 Coordinator Email: ${coordEmail}`);
  console.log(`🏢 Assigned Hospital: ${aiims.name}`);
  console.log('======================================================\n');

  process.exit(0);
}

seedOperationalAccounts().catch((err) => {
  console.error('❌ Error seeding accounts:', err);
  process.exit(1);
});
