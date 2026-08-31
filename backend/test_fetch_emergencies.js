require('dotenv').config({ path: require('path').join(__dirname, '.env') });
require('dns').setServers(['8.8.8.8', '8.8.4.4']);
const mongoose = require('mongoose');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');

async function testFetch() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log('Connected to MongoDB Atlas');

  const allEmergencies = await EmergencyRequest.find({});
  console.log(`Total Emergency Requests in DB: ${allEmergencies.length}`);

  for (const em of allEmergencies) {
    console.log(`- ID: ${em._id}, Status: ${em.status}, Patient: ${em.patientName}, Hospital: ${em.hospital}, CreatedAt: ${em.createdAt}`);
    const responses = await EmergencyResponse.find({ requestId: em._id });
    console.log(`  Responses count: ${responses.length}`);
  }

  process.exit(0);
}

testFetch().catch(e => { console.error(e); process.exit(1); });
