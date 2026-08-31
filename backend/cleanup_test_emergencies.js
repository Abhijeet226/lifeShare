require('dotenv').config({ path: require('path').join(__dirname, '.env') });
require('dns').setServers(['8.8.8.8', '8.8.4.4']);
const mongoose = require('mongoose');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');

async function cleanup() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log('Connected to MongoDB Atlas');

  // Purge test fraud scenarios from database
  const res = await EmergencyRequest.deleteMany({
    patientName: { $regex: /fraud/i }
  });
  console.log(`Deleted ${res.deletedCount} test fraud scenario emergencies.`);

  // Verify active list
  const active = await EmergencyRequest.find({
    status: { $nin: ['CANCELLED', 'EXPIRED', 'COMPLETED'] },
    isActive: { $ne: false }
  });
  console.log(`Current Clean Active Emergency Requests in DB: ${active.length}`);
  active.forEach(a => console.log(`- [${a.bloodGroup}] ${a.patientName} at ${a.hospital} (Status: ${a.status})`));

  process.exit(0);
}

cleanup().catch(e => { console.error(e); process.exit(1); });
