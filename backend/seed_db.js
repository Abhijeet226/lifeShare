require('dotenv').config();
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const connectDB = require('./config/database');

const User = require('./models/User');
const EmergencyRequest = require('./models/EmergencyRequest');
const Hospital = require('./models/Hospital');
const BloodBank = require('./models/BloodBank');

const sampleDonors = [
  // Bhubaneswar Donors (Coordinates ~ 20.2961, 85.8245)
  {
    firstName: 'Abhijeet',
    lastName: 'Pradhan',
    dob: '2000-05-15',
    gender: 'Male',
    email: 'abhijeet.p@lifeshare.org',
    mobile: '+91 9820112233',
    bloodGroup: 'O+',
    city: 'Bhubaneswar',
    location: { type: 'Point', coordinates: [85.8245, 20.2961] } // [lng, lat]
  },
  {
    firstName: 'Tanushree',
    lastName: 'Das',
    dob: '2000-04-18',
    gender: 'Female',
    email: 'tanu.d@lifeshare.org',
    mobile: '+91 9845678901',
    bloodGroup: 'A+',
    city: 'Bhubaneswar',
    location: { type: 'Point', coordinates: [85.8189, 20.3012] }
  },
  {
    firstName: 'Deepak',
    lastName: 'Routray',
    dob: '1995-10-05',
    gender: 'Male',
    email: 'deepak.r@lifeshare.org',
    mobile: '+91 9867890123',
    bloodGroup: 'B+',
    city: 'Bhubaneswar',
    location: { type: 'Point', coordinates: [85.8350, 20.2700] }
  },
  {
    firstName: 'Ananya',
    lastName: 'Panigrahi',
    dob: '2002-01-15',
    gender: 'Female',
    email: 'ananya.p@lifeshare.org',
    mobile: '+91 9889012345',
    bloodGroup: 'AB+',
    city: 'Bhubaneswar',
    location: { type: 'Point', coordinates: [85.7900, 20.3200] }
  },
  {
    firstName: 'Aditya',
    lastName: 'Nath',
    dob: '1996-03-24',
    gender: 'Male',
    email: 'aditya.n@lifeshare.org',
    mobile: '+91 9823456789',
    bloodGroup: 'O-',
    city: 'Bhubaneswar',
    location: { type: 'Point', coordinates: [85.8120, 20.2850] }
  },

  // Cuttack Donors (~ 20.4625, 85.8830)
  {
    firstName: 'Soumya',
    lastName: 'Mohanty',
    dob: '1998-08-22',
    gender: 'Male',
    email: 'soumya.m@lifeshare.org',
    mobile: '+91 9811223344',
    bloodGroup: 'A+',
    city: 'Cuttack',
    location: { type: 'Point', coordinates: [85.8830, 20.4625] }
  },
  {
    firstName: 'Smruti',
    lastName: 'Sahoo',
    dob: '2001-07-14',
    gender: 'Female',
    email: 'smruti.s@lifeshare.org',
    mobile: '+91 9812345678',
    bloodGroup: 'O+',
    city: 'Cuttack',
    location: { type: 'Point', coordinates: [85.8750, 20.4550] }
  },

  // Rourkela Donors (~ 22.2570, 84.8640)
  {
    firstName: 'Priyanka',
    lastName: 'Patra',
    dob: '2001-11-04',
    gender: 'Female',
    email: 'priyanka.p@lifeshare.org',
    mobile: '+91 9833445566',
    bloodGroup: 'B+',
    city: 'Rourkela',
    location: { type: 'Point', coordinates: [84.8640, 22.2570] }
  },
  {
    firstName: 'Chinmayee',
    lastName: 'Mishra',
    dob: '2001-08-29',
    gender: 'Female',
    email: 'chinmayee.m@lifeshare.org',
    mobile: '+91 9856789012',
    bloodGroup: 'A-',
    city: 'Rourkela',
    location: { type: 'Point', coordinates: [84.8500, 22.2400] }
  }
];

const sampleHospitals = [
  {
    name: 'AIIMS Bhubaneswar',
    address: 'Sijua, Patrapada',
    city: 'Bhubaneswar',
    phone: '0674-2476789',
    location: { type: 'Point', coordinates: [85.7770, 20.2289] }
  },
  {
    name: 'SCB Medical College & Hospital',
    address: 'Mangalabag',
    city: 'Cuttack',
    phone: '0671-2414080',
    location: { type: 'Point', coordinates: [85.8830, 20.4625] }
  },
  {
    name: 'Capital Hospital',
    address: 'Unit-6, Ganga Nagar',
    city: 'Bhubaneswar',
    phone: '0674-2391983',
    location: { type: 'Point', coordinates: [85.8210, 20.2710] }
  },
  {
    name: 'Ispat General Hospital (IGH)',
    address: 'Sector-19',
    city: 'Rourkela',
    phone: '0661-2646222',
    location: { type: 'Point', coordinates: [84.8640, 22.2570] }
  },
  {
    name: 'MKCG Medical College',
    address: 'Berhampur Medical Campus',
    city: 'Berhampur',
    phone: '0680-2292744',
    location: { type: 'Point', coordinates: [84.7940, 19.3140] }
  }
];

const sampleBloodBanks = [
  {
    name: 'AIIMS Blood Center',
    address: 'Sijua, Patrapada',
    city: 'Bhubaneswar',
    contactNumber: '0674-2476789',
    availableUnits: 25,
    location: { type: 'Point', coordinates: [85.7770, 20.2289] }
  },
  {
    name: 'Red Cross Blood Bank',
    address: 'Unit-4, Bhouma Nagar',
    city: 'Bhubaneswar',
    contactNumber: '0674-2501064',
    availableUnits: 40,
    location: { type: 'Point', coordinates: [85.8338, 20.2724] }
  },
  {
    name: 'SCB Central Blood Bank',
    address: 'Mangalabag',
    city: 'Cuttack',
    contactNumber: '0671-2414080',
    availableUnits: 50,
    location: { type: 'Point', coordinates: [85.8830, 20.4625] }
  }
];

const sampleRequests = [
  {
    patientName: 'Ramesh Chandra Jena',
    hospital: 'AIIMS Bhubaneswar',
    city: 'Bhubaneswar',
    bloodGroup: 'O+',
    unitsRequired: 3,
    unitsNeeded: 3,
    contactNumber: '+91 9820112233',
    urgency: 'CRITICAL',
    requestLocation: { type: 'Point', coordinates: [85.7770, 20.2289] },
    hospitalLocation: { type: 'Point', coordinates: [85.7770, 20.2289] }
  },
  {
    patientName: 'Snehalata Mishra',
    hospital: 'SCB Medical College & Hospital',
    city: 'Cuttack',
    bloodGroup: 'AB-',
    unitsRequired: 2,
    unitsNeeded: 2,
    contactNumber: '+91 9855667788',
    urgency: 'URGENT',
    requestLocation: { type: 'Point', coordinates: [85.8830, 20.4625] },
    hospitalLocation: { type: 'Point', coordinates: [85.8830, 20.4625] }
  }
];

async function seedDatabase() {
  try {
    await connectDB();
    console.log('Seeding realistic Odisha geospatial test data...');

    const defaultPassword = await bcrypt.hash('password123', 10);

    // Seed Donors
    for (const donor of sampleDonors) {
      const cleanEmail = donor.email.toLowerCase().trim();
      const existing = await User.findOne({ email: cleanEmail });
      if (!existing) {
        await new User({
          ...donor,
          name: `${donor.firstName} ${donor.lastName}`,
          password: defaultPassword,
          isAvailable: true,
          locationUpdatedAt: new Date()
        }).save();
      } else {
        await User.updateOne({ _id: existing._id }, { location: donor.location, locationUpdatedAt: new Date() });
      }
    }

    // Seed Hospitals
    for (const h of sampleHospitals) {
      const existing = await Hospital.findOne({ name: h.name });
      if (!existing) {
        await new Hospital(h).save();
      }
    }

    // Seed Blood Banks
    for (const b of sampleBloodBanks) {
      const existing = await BloodBank.findOne({ name: b.name });
      if (!existing) {
        await new BloodBank(b).save();
      }
    }

    // Seed Sample Requests
    for (const req of sampleRequests) {
      const existing = await EmergencyRequest.findOne({ patientName: req.patientName });
      if (!existing) {
        await new EmergencyRequest(req).save();
      }
    }

    console.log('✅ LifeShare V2 Database successfully seeded with 2dsphere Geospatial records!');
    process.exit(0);
  } catch (err) {
    console.error('❌ Seeding error:', err.message);
    process.exit(1);
  }
}

seedDatabase();
