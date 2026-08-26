require('dotenv').config();
const mongoose = require('mongoose');
const dns = require('dns');

// Fix Node.js on Windows DNS SRV resolution issue with Google / Cloudflare DNS
try {
  dns.setServers(['8.8.8.8', '1.1.1.1']);
} catch (e) {}

const uri = process.env.MONGO_URI;

console.log('Testing MongoDB connection with DNS fix...');
console.log(uri.replace(/:([^:@]+)@/, ':****@'));

mongoose.connect(uri)
  .then(() => {
    console.log('\n=============================================');
    console.log('🎉 SUCCESS: Connected to MongoDB Atlas (Lifeshare) successfully!');
    console.log('Database Name:', mongoose.connection.name);
    console.log('Ready State:', mongoose.connection.readyState);
    console.log('=============================================\n');
    process.exit(0);
  })
  .catch(err => {
    console.error('\n=============================================');
    console.error('❌ FAILED: MongoDB Atlas Connection Error:');
    console.error(err.message);
    console.error('=============================================\n');
    process.exit(1);
  });
