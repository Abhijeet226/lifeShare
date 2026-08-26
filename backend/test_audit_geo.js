require('dotenv').config();
const connectDB = require('./config/database');
const User = require('./models/User');

async function testGeo() {
  await connectDB();
  const allUsers = await User.find({});
  console.log('Total Users in MongoDB:', allUsers.length);
  
  const usersWithLoc = await User.find({ 'location.coordinates': { $exists: true, $ne: [] } });
  console.log('Users with Geo coordinates:', usersWithLoc.length);
  usersWithLoc.forEach(u => console.log('•', u.name, u.bloodGroup, u.location?.coordinates, 'isAvailable:', u.isAvailable));

  // Run geoNear at Bhubaneswar (lng: 85.8245, lat: 20.2961)
  try {
    const geoRes = await User.aggregate([
      {
        $geoNear: {
          near: { type: 'Point', coordinates: [85.8245, 20.2961] },
          distanceField: 'distanceMeters',
          maxDistance: 30000,
          spherical: true
        }
      }
    ]);
    console.log('\nGeoNear 30km from Bhubaneswar result count:', geoRes.length);
    geoRes.forEach(r => console.log('  ->', r.name, r.bloodGroup, (r.distanceMeters / 1000).toFixed(2) + ' km away'));
  } catch (err) {
    console.error('GeoNear aggregation error:', err.message);
  }
  process.exit(0);
}

testGeo();
