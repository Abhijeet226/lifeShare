/**
 * Geospatial distance calculation using Haversine formula
 * Returns distance in meters between two points
 */
function calculateDistanceMeters(lat1, lon1, lat2, lon2) {
  if (lat1 === lat2 && lon1 === lon2) return 0;
  const R = 6371e3; // Earth radius in meters
  const radLat1 = (lat1 * Math.PI) / 180;
  const radLat2 = (lat2 * Math.PI) / 180;
  const deltaLat = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLon = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
    Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return Math.round(R * c);
}

function isValidCoordinate(lng, lat) {
  return (
    typeof lng === 'number' &&
    typeof lat === 'number' &&
    !isNaN(lng) &&
    !isNaN(lat) &&
    lng >= -180 &&
    lng <= 180 &&
    lat >= -90 &&
    lat <= 90
  );
}

const ODISHA_CITIES = {
  bhubaneswar: { lat: 20.2961, lng: 85.8245 },
  cuttack: { lat: 20.4625, lng: 85.8828 },
  rourkela: { lat: 22.2604, lng: 84.8536 },
  berhampur: { lat: 19.3150, lng: 84.7941 },
  sambalpur: { lat: 21.4669, lng: 83.9812 },
  puri: { lat: 19.8135, lng: 85.8312 },
  balasore: { lat: 21.4934, lng: 86.9135 },
  baripada: { lat: 21.9348, lng: 86.7369 },
  bhadrak: { lat: 21.0543, lng: 86.4975 },
  angul: { lat: 20.8398, lng: 85.1017 },
  dhenkanal: { lat: 20.6607, lng: 85.5967 },
  jharsuguda: { lat: 21.8554, lng: 84.0062 },
  jeypore: { lat: 18.8576, lng: 82.5694 },
  koraput: { lat: 18.8135, lng: 82.7118 },
  kendujhar: { lat: 21.6289, lng: 85.5817 }
};

function getCityCoordinates(cityName) {
  if (!cityName) return null;
  const key = cityName.toLowerCase().trim();
  return ODISHA_CITIES[key] || { lat: 20.2961, lng: 85.8245 };
}

module.exports = {
  calculateDistanceMeters,
  isValidCoordinate,
  getCityCoordinates
};
