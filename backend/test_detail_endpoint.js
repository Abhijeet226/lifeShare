require('dotenv').config({ path: require('path').join(__dirname, '.env') });
require('dns').setServers(['8.8.8.8', '8.8.4.4']);
const mongoose = require('mongoose');
const EmergencyRequest = require('./models/EmergencyRequest');
const EmergencyResponse = require('./models/EmergencyResponse');
const User = require('./models/User');
const City = require('./models/City');

async function testDetailEndpoint() {
  await mongoose.connect(process.env.MONGO_URI);
  console.log('Connected to MongoDB Atlas');

  const id = '6a8537621483b723520485af';
  const emergency = await EmergencyRequest.findById(id).populate('cityId', 'name stateName location');
  console.log('Emergency found:', !!emergency);
  if (!emergency) return;

  try {
    const responses = await EmergencyResponse.find({ requestId: emergency._id })
      .populate('donorId', 'name bloodGroup verificationStatus accountStatus donorId');
    console.log('Responses count:', responses.length);
    console.log('Response sample:', responses);
  } catch (err) {
    console.error('Error querying responses:', err);
  }

  process.exit(0);
}

testDetailEndpoint().catch(e => { console.error(e); process.exit(1); });
