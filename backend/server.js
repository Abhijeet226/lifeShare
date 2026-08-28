require('dotenv').config();
const express = require('express');
const cors = require('cors');
const connectDB = require('./config/database');

const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/users');
const donorRoutes = require('./routes/donors');
const emergencyRoutes = require('./routes/emergencies');
const deviceRoutes = require('./routes/devices');
const hospitalRoutes = require('./routes/hospitals');
const bloodBankRoutes = require('./routes/bloodbanks');
const donationRoutes = require('./routes/donations');
const cityRoutes = require('./routes/cities');
const adminRoutes = require('./routes/admin');
const campRoutes = require('./routes/camps');
const chatRoutes = require('./routes/chat');

const app = express();
const PORT = process.env.PORT || 5000;

// Global Middleware
app.use(cors());
app.use(express.json());

// API Routes
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/donors', donorRoutes);
app.use('/api/emergency', emergencyRoutes);
app.use('/api/emergencies', emergencyRoutes);
app.use('/api/device-tokens', deviceRoutes);
app.use('/api/hospitals', hospitalRoutes);
app.use('/api/bloodbanks', bloodBankRoutes);
app.use('/api/donations', donationRoutes);
app.use('/api/cities', cityRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/camps', campRoutes);
app.use('/api/chat', chatRoutes);

// Health check
app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    version: '2.0.0',
    database: 'MongoDB Atlas - Lifeshare V2 (Geospatial Enabled)',
    timestamp: new Date()
  });
});

// Root route
app.get('/', (req, res) => {
  res.json({
    name: 'LifeShare REST API V2',
    status: 'online',
    docs: '/api/health'
  });
});

// JSON 404 Catch-All Middleware
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: `Resource not found: ${req.method} ${req.originalUrl}`
  });
});

// Global Error Handler Middleware
app.use((err, req, res, next) => {
  console.error('Unhandled API Error:', err);
  res.status(err.status || 500).json({
    success: false,
    message: err.message || 'Internal server error occurred'
  });
});

// Global Process Diagnostics
process.on('unhandledRejection', (reason, promise) => {
  console.error('⚠️ Unhandled Promise Rejection at:', promise, 'reason:', reason);
});

process.on('uncaughtException', (err) => {
  console.error('🚨 Uncaught Exception thrown:', err);
});

// Connect to MongoDB Atlas & Start Server
connectDB()
  .then(() => {
    app.listen(PORT, '0.0.0.0', () => {
      console.log(`🚀 LifeShare V2 Server running on:`);
      console.log(`   • Localhost:       http://localhost:${PORT}/api`);
      console.log(`   • Android Emulator: http://10.0.2.2:${PORT}/api`);
      console.log(`   • Health check:     http://localhost:${PORT}/api/health`);
    });
  })
  .catch((err) => {
    console.error('❌ Could not start LifeShare server:', err.message);
  });

module.exports = app;
