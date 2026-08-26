require('dotenv').config({ path: require('path').join(__dirname, '../.env') });
const mongoose = require('mongoose');
const City = require('../models/City');
const Hospital = require('../models/Hospital');
const connectDB = require('../config/database');

const ODISHA_CITIES = [
  { name: 'Bhubaneswar', lng: 85.8245, lat: 20.2961 },
  { name: 'Cuttack', lng: 85.8828, lat: 20.4625 },
  { name: 'Rourkela', lng: 84.8536, lat: 22.2604 },
  { name: 'Berhampur', lng: 84.7941, lat: 19.3150 },
  { name: 'Sambalpur', lng: 83.9856, lat: 21.4669 },
  { name: 'Puri', lng: 85.8312, lat: 19.8135 },
  { name: 'Balasore', lng: 86.9324, lat: 21.4934 },
  { name: 'Bhadrak', lng: 86.5167, lat: 21.0574 },
  { name: 'Baripada', lng: 86.7277, lat: 21.9322 },
  { name: 'Jharsuguda', lng: 84.0084, lat: 21.8554 },
  { name: 'Jeypore', lng: 82.5562, lat: 18.8558 },
  { name: 'Angul', lng: 85.0985, lat: 20.8444 },
  { name: 'Dhenkanal', lng: 85.5960, lat: 20.6593 },
  { name: 'Bargarh', lng: 83.6208, lat: 21.3340 },
  { name: 'Rayagada', lng: 83.4163, lat: 19.1718 },
  { name: 'Kendujhar', lng: 85.5824, lat: 21.6289 },
  { name: 'Bolangir', lng: 83.4883, lat: 20.7073 },
  { name: 'Kendrapara', lng: 86.4230, lat: 20.5029 },
  { name: 'Jagatsinghpur', lng: 86.1717, lat: 20.2646 },
  { name: 'Jajpur', lng: 86.3323, lat: 20.8524 }
];

const ODISHA_HOSPITALS = [
  // Bhubaneswar
  {
    name: 'AIIMS Bhubaneswar',
    address: 'Sijua, Patrapada',
    city: 'Bhubaneswar',
    phone: '0674-2476789',
    lng: 85.7770,
    lat: 20.2289
  },
  {
    name: 'Capital Hospital',
    address: 'Unit-6, Ganga Nagar',
    city: 'Bhubaneswar',
    phone: '0674-2391983',
    lng: 85.8210,
    lat: 20.2710
  },
  {
    name: 'Apollo Hospitals',
    address: 'Plot No. 251, Sainik School Rd, Unit 15',
    city: 'Bhubaneswar',
    phone: '0674-6661016',
    lng: 85.8338,
    lat: 20.3032
  },
  {
    name: 'KIMS Hospital',
    address: 'KIIT Road, Patia',
    city: 'Bhubaneswar',
    phone: '0674-7105300',
    lng: 85.8189,
    lat: 20.3540
  },
  {
    name: 'SUM Hospital & Medical College',
    address: 'K8, Kalinga Nagar',
    city: 'Bhubaneswar',
    phone: '0674-2386292',
    lng: 85.7650,
    lat: 20.2780
  },

  // Cuttack
  {
    name: 'SCB Medical College & Hospital',
    address: 'Mangalabag',
    city: 'Cuttack',
    phone: '0671-2414080',
    lng: 85.8830,
    lat: 20.4625
  },
  {
    name: 'Ashwini Hospital',
    address: 'Sector 1, CDA Market Complex',
    city: 'Cuttack',
    phone: '0671-2363007',
    lng: 85.8450,
    lat: 20.4850
  },
  {
    name: 'Shanti Memorial Hospital',
    address: 'Thoria Sahi, Mangalabag',
    city: 'Cuttack',
    phone: '0671-2415240',
    lng: 85.8890,
    lat: 20.4680
  },

  // Rourkela
  {
    name: 'Ispat General Hospital (IGH)',
    address: 'Sector-19',
    city: 'Rourkela',
    phone: '0661-2646222',
    lng: 84.8640,
    lat: 22.2570
  },
  {
    name: 'Rourkela Government Hospital (RGH)',
    address: 'Panposh Road',
    city: 'Rourkela',
    phone: '0661-2401333',
    lng: 84.8510,
    lat: 22.2410
  },

  // Berhampur
  {
    name: 'MKCG Medical College & Hospital',
    address: 'Medical College Campus',
    city: 'Berhampur',
    phone: '0680-2292744',
    lng: 84.7940,
    lat: 19.3140
  },
  {
    name: 'City Hospital Berhampur',
    address: 'Old Berhampur Main Road',
    city: 'Berhampur',
    phone: '0680-2220145',
    lng: 84.7890,
    lat: 19.3090
  },

  // Sambalpur
  {
    name: 'VIMSAR Medical College',
    address: 'Burla Medical Campus',
    city: 'Sambalpur',
    phone: '0663-2430768',
    lng: 83.8740,
    lat: 21.5030
  },
  {
    name: 'District Headquarter Hospital Sambalpur',
    address: 'Kacheri Road',
    city: 'Sambalpur',
    phone: '0663-2400320',
    lng: 83.9856,
    lat: 21.4669
  },

  // Puri
  {
    name: 'Shri Jagannath Medical College (SJMCH)',
    address: 'Samanga, Puri',
    city: 'Puri',
    phone: '06752-297001',
    lng: 85.8150,
    lat: 19.8240
  },
  {
    name: 'District Headquarter Hospital Puri',
    address: 'Grand Road',
    city: 'Puri',
    phone: '06752-222038',
    lng: 85.8312,
    lat: 19.8135
  },

  // Balasore
  {
    name: 'Fakir Mohan Medical College (FMMCH)',
    address: 'Remuna',
    city: 'Balasore',
    phone: '06782-224888',
    lng: 86.8720,
    lat: 21.5280
  },

  // Bhadrak
  {
    name: 'District Headquarter Hospital Bhadrak',
    address: 'Apartibindha',
    city: 'Bhadrak',
    phone: '06784-251210',
    lng: 86.5167,
    lat: 21.0574
  },

  // Baripada
  {
    name: 'Pandit Raghunath Murmu Medical College (PRMMCH)',
    address: 'Rangamatia, Baripada',
    city: 'Baripada',
    phone: '06792-252002',
    lng: 86.7277,
    lat: 21.9322
  },

  // Jharsuguda
  {
    name: 'District Headquarter Hospital Jharsuguda',
    address: 'Mangalbazar',
    city: 'Jharsuguda',
    phone: '06645-272023',
    lng: 84.0084,
    lat: 21.8554
  },

  // Jeypore
  {
    name: 'Saheed Laxman Nayak Medical College (SLNMCH)',
    address: 'Medical Road, Koraput / Jeypore Highway',
    city: 'Jeypore',
    phone: '06852-250100',
    lng: 82.7100,
    lat: 18.8100
  },

  // Angul
  {
    name: 'District Headquarter Hospital Angul',
    address: 'Amalapada',
    city: 'Angul',
    phone: '06764-230420',
    lng: 85.0985,
    lat: 20.8444
  },

  // Dhenkanal
  {
    name: 'District Headquarter Hospital Dhenkanal',
    address: 'Station Road',
    city: 'Dhenkanal',
    phone: '06762-224420',
    lng: 85.5960,
    lat: 20.6593
  },

  // Bargarh
  {
    name: 'District Headquarter Hospital Bargarh',
    address: 'Khedapali',
    city: 'Bargarh',
    phone: '06646-234200',
    lng: 83.6208,
    lat: 21.3340
  },

  // Rayagada
  {
    name: 'District Headquarter Hospital Rayagada',
    address: 'Hospital Road',
    city: 'Rayagada',
    phone: '06856-222045',
    lng: 83.4163,
    lat: 19.1718
  },

  // Kendujhar
  {
    name: 'Dharanidhar Medical College (DDMCH)',
    address: 'Old Town',
    city: 'Kendujhar',
    phone: '06766-255010',
    lng: 85.5824,
    lat: 21.6289
  },

  // Bolangir
  {
    name: 'Bhima Bhoi Medical College (BBMCH)',
    address: 'Gandhi Nagar',
    city: 'Bolangir',
    phone: '06652-230300',
    lng: 83.4883,
    lat: 20.7073
  },

  // Kendrapara
  {
    name: 'District Headquarter Hospital Kendrapara',
    address: 'Main Road',
    city: 'Kendrapara',
    phone: '06727-232040',
    lng: 86.4230,
    lat: 20.5029
  },

  // Jagatsinghpur
  {
    name: 'District Headquarter Hospital Jagatsinghpur',
    address: 'Court Road',
    city: 'Jagatsinghpur',
    phone: '06724-220025',
    lng: 86.1717,
    lat: 20.2646
  },

  // Jajpur
  {
    name: 'Jajpur Medical College (JJMCH)',
    address: 'Ankula',
    city: 'Jajpur',
    phone: '06728-222030',
    lng: 86.3323,
    lat: 20.8524
  }
];

async function seedCanonicalCities() {
  await connectDB();
  console.log('🏙️  Seeding Canonical Odisha Cities...');

  const cityMap = {};

  for (const item of ODISHA_CITIES) {
    const normalized = item.name.trim().toLowerCase();
    const cityDoc = await City.findOneAndUpdate(
      { normalizedName: normalized },
      {
        $setOnInsert: {
          name: item.name,
          normalizedName: normalized,
          stateName: 'Odisha',
          stateCode: 'OD',
          countryName: 'India',
          countryCode: 'IN',
          location: {
            type: 'Point',
            coordinates: [item.lng, item.lat]
          },
          isActive: true
        }
      },
      { upsert: true, new: true }
    );
    cityMap[item.name.toLowerCase()] = cityDoc._id;
    console.log(`  ✓ City: ${cityDoc.name} [${cityDoc._id}]`);
  }

  console.log('\n🏥 Seeding Comprehensive Verified Hospitals for All Cities...');
  for (const h of ODISHA_HOSPITALS) {
    const cityId = cityMap[h.city.toLowerCase()] || null;
    const hospitalDoc = await Hospital.findOneAndUpdate(
      { name: h.name, cityId: cityId },
      {
        $set: {
          address: h.address,
          phone: h.phone,
          verified: true,
          isVerified: true,
          emergencySupport: true,
          cityId: cityId,
          location: {
            type: 'Point',
            coordinates: [h.lng, h.lat]
          }
        },
        $unset: {
          city: 1
        }
      },
      { upsert: true, new: true }
    );
    console.log(`  ✓ Hospital: ${hospitalDoc.name} [${hospitalDoc._id}]`);
  }

  // Clean up legacy city fields from Hospital, EmergencyRequest, BloodBank collections
  await Hospital.updateMany({}, { $unset: { city: 1 } });
  console.log('  ✓ Cleaned legacy static city fields from Hospital collection');

  const cityCount = await City.countDocuments();
  const hospitalCount = await Hospital.countDocuments();
  console.log(`\n✅ Total Canonical Cities: ${cityCount}`);
  console.log(`✅ Total Verified Hospitals in DB: ${hospitalCount}`);
}

if (require.main === module) {
  seedCanonicalCities()
    .then(() => mongoose.connection.close())
    .catch((err) => {
      console.error('❌ Seeding failed:', err);
      process.exit(1);
    });
}

module.exports = seedCanonicalCities;
