/*
 * Generates the retained expanded RailSafe dataset.
 *
 * This is a restricted source-review dataset only. The importer classifies every
 * generated row as restricted and exposes it exclusively in the Super Admin
 * Source Review area; no normal dashboard, map, alert, report or API feed uses it.
 */
const fs = require("fs");
const path = require("path");

const gates = [];
const trains = [];
const sensors = [];
const predictions = [];
const countdowns = [];

const gateStatuses = ["OPEN", "CLOSING", "CLOSED", "OPENING"];
const sensorStatuses = ["ACTIVE", "INACTIVE", "MAINTENANCE"];
const generatedAt = new Date().toISOString();
const restrictedSource = "RESTRICTED_DATASET";

// Original 3 gates + 150 additional gates
for (let i = 1; i <= 153; i++) {
  const id = `GATE${String(i).padStart(3, "0")}`;
  const status = gateStatuses[(i - 1) % gateStatuses.length];
  const sensorStatus = sensorStatuses[(i - 1) % sensorStatuses.length];
  const associatedTrainCount = i <= 23 ? 1 : 0;

  gates.push({
    gateId: id,
    gateName: `Synthetic Railway Gate ${i}`,
    location: `Test Zone ${i}, Telangana`,
    latitude: 17.3 + (i % 100) / 1000,
    longitude: 78.3 + (i % 100) / 1000,
    gateStatus: status,
    sensorStatus,
    associatedTrainCount,
    nearbyTrainCount: associatedTrainCount,
    lastUpdated: generatedAt,
    dataSource: restrictedSource,
    dataMode: "SIMULATION"
  });

  sensors.push({
    sensorId: `SENSOR${String(i).padStart(3, "0")}`,
    gateId: id,
    sensorType: "GATE_POSITION_SENSOR",
    sensorStatus,
    gateStatus: status,
    batteryLevel: 60 + (i * 7) % 41,
    signalStrength: 55 + (i * 9) % 46,
    lastUpdated: generatedAt,
    dataSource: restrictedSource,
    dataMode: "SIMULATION"
  });
}

// Original 3 trains + 20 additional trains
const originalTrains = [
  {
    trainNumber: "12727",
    trainName: "Godavari Express",
    gateId: "GATE001",
    direction: "NORTH",
    source: "Hyderabad",
    destination: "Visakhapatnam",
    distanceFromGate: 5.2,
    speed: 80,
    etaMinutes: 4,
    status: "APPROACHING"
  },
  {
    trainNumber: "12728",
    trainName: "Godavari Express",
    gateId: "GATE002",
    direction: "SOUTH",
    source: "Visakhapatnam",
    destination: "Hyderabad",
    distanceFromGate: 2.5,
    speed: 70,
    etaMinutes: 3,
    status: "NEAR_GATE"
  },
  {
    trainNumber: "17016",
    trainName: "Visakha Express",
    gateId: "GATE003",
    direction: "EAST",
    source: "Secunderabad",
    destination: "Bhubaneswar",
    distanceFromGate: 0,
    speed: 0,
    etaMinutes: 0,
    status: "PASSED"
  }
];

trains.push(...originalTrains);

const directions = ["NORTH", "SOUTH", "EAST", "WEST"];
const statuses = [
  "APPROACHING",
  "NEAR_GATE",
  "CROSSING",
  "PASSED"
];

for (let i = 4; i <= 23; i++) {
  const distance = 2 + (i % 8) * 1.5;
  const speed = 60 + (i % 5) * 10;
  const status = statuses[i % 4];

  trains.push({
    // 27000-range avoids the three original train numbers, including 17016.
    trainNumber: String(27000 + i),
    trainName: `Synthetic Express ${i}`,
    gateId: `GATE${String(i).padStart(3, "0")}`,
    direction: directions[i % 4],
    source: `Synthetic Origin ${i}`,
    destination: `Synthetic Destination ${i}`,
    distanceFromGate: status === "PASSED" ? 0 : distance,
    speed: status === "PASSED" ? 0 : speed,
    etaMinutes: status === "PASSED" ? 0 :
      Math.ceil(distance / speed * 60),
    status
  });
}

// Every restricted train carries the same identifying/source fields. These records
// are review-only and are never promoted to operational train feeds.
trains.forEach((train, index) => {
  train.trainId = `TRAIN${String(index + 1).padStart(3, "0")}`;
  train.lastUpdated = generatedAt;
  train.dataSource = restrictedSource;
  train.dataMode = "SIMULATION";
});

// Generate prediction and countdown records
trains.forEach(train => {
  if (["APPROACHING", "NEAR_GATE"].includes(train.status)) {
    predictions.push({
      gateId: train.gateId,
      trainNumber: train.trainNumber,
      estimatedArrivalMinutes: train.etaMinutes,
      predictionStatus: "TRAIN_APPROACHING",
      confidence: 85,
      dataSource: restrictedSource,
      dataMode: "SIMULATION",
      lastUpdated: generatedAt
    });

    const gate = gates.find(g => g.gateId === train.gateId);
    const arrivalMs = Date.parse(generatedAt) + train.etaMinutes * 60 * 1000;
    const arrivalAt = new Date(arrivalMs).toISOString();
    const closingMs = ["OPEN", "CLOSING"].includes(gate.gateStatus)
      ? Math.max(Date.parse(generatedAt), arrivalMs - 60 * 1000)
      : null;
    const openingMs = ["CLOSED", "OPENING"].includes(gate.gateStatus)
      ? arrivalMs + 8 * 60 * 1000
      : null;

    countdowns.push({
      countdownId: `COUNTDOWN${String(countdowns.length + 1).padStart(3, "0")}`,
      gateId: train.gateId,
      gateStatus: gate.gateStatus,
      trainId: train.trainId,
      trainNumber: train.trainNumber,
      trainStatus: train.status,
      trainArrivalAt: arrivalAt,
      trainArrivalSeconds: train.etaMinutes * 60,
      gateClosingAt: closingMs === null ? null : new Date(closingMs).toISOString(),
      gateClosingSeconds: closingMs === null
        ? null : Math.max(0, Math.round((closingMs - Date.parse(generatedAt)) / 1000)),
      expectedGateOpeningAt: openingMs === null ? null : new Date(openingMs).toISOString(),
      expectedGateOpeningSeconds: openingMs === null
        ? null : Math.max(0, Math.round((openingMs - Date.parse(generatedAt)) / 1000)),
      // Retained for compatibility with the original review payload.
      closingCountdownSeconds: train.etaMinutes * 60,
      lastUpdated: generatedAt,
      dataSource: restrictedSource,
      dataMode: "SIMULATION"
    });
  }
});

const openGates = gates.filter(g => g.gateStatus === "OPEN").length;
const closingGates = gates.filter(g => g.gateStatus === "CLOSING").length;
const closedGates = gates.filter(g => g.gateStatus === "CLOSED").length;
const openingGates = gates.filter(g => g.gateStatus === "OPENING").length;

const activeSensors = sensors.filter(s => s.sensorStatus === "ACTIVE").length;
const inactiveSensors = sensors.filter(s => s.sensorStatus === "INACTIVE").length;
const maintenanceSensors = sensors.filter(s => s.sensorStatus === "MAINTENANCE").length;

const data = {
  project: "RailSafe Railway Gate Alert System",
  dataMode: "SIMULATION",

  dashboard: {
    totalGates: gates.length,
    openGates,
    closingGates,
    closedGates,
    openingGates,
    totalTrains: trains.length,
    approachingTrains: trains.filter(
      t => t.status === "APPROACHING"
    ).length,
    nearGateTrains: trains.filter(
      t => t.status === "NEAR_GATE"
    ).length,
    crossingTrains: trains.filter(
      t => t.status === "CROSSING"
    ).length,
    passedTrains: trains.filter(
      t => t.status === "PASSED"
    ).length,
    activeSensors,
    inactiveSensors,
    maintenanceSensors,
    activeAlerts: predictions.length
  },

  gates,
  trains,
  sensors,
  predictions,
  countdowns,

  notifications: [
    {
      notificationId: "NOT001",
      gateId: "GATE001",
      message: "Synthetic train approaching alert",
      notificationType: "GATE_CLOSING_ALERT",
      channel: "EMAIL",
      status: "SAMPLE"
    }
  ],

  alternativeRoutes: [
    {
      routeId: "ROUTE001",
      gateId: "GATE001",
      routeName: "Synthetic Alternative Route A",
      distanceKm: 4.5,
      estimatedTravelMinutes: 8,
      status: "SAMPLE"
    }
  ],

  users: [
    {
      userId: 1,
      name: "Sample User",
      email: "user@example.com",
      role: "USER",
      status: "ACTIVE"
    }
  ],

  admins: [
    {
      adminId: 1,
      name: "Sample Admin",
      email: "admin@example.com",
      role: "ADMIN",
      status: "ACTIVE"
    }
  ],

  superAdmin: {
    adminId: 100,
    name: "System Super Admin",
    email: "superadmin@example.com",
    role: "SUPER_ADMIN",
    permissions: [
      "MANAGE_USERS",
      "MANAGE_ADMINS",
      "MANAGE_GATES",
      "MANAGE_TRAINS",
      "MANAGE_ALERTS",
      "VIEW_ANALYTICS",
      "MANAGE_SYSTEM_SETTINGS",
      "VIEW_AUDIT_LOGS"
    ]
  },

  system: {
    systemName: "RailSafe",
    version: "1.0",
    environment: "DEVELOPMENT",
    dataMode: "SIMULATION",
    systemStatus: "RUNNING"
  }
};

const outputPath = path.join(__dirname, "..", "data", "railsafe-expanded-data.json");
fs.writeFileSync(outputPath, JSON.stringify(data, null, 2));

console.log("RailSafe retained dataset generated successfully!");
console.log("Output:", outputPath);
console.log("Gates:", gates.length);
console.log("Trains:", trains.length);
console.log("Sensors:", sensors.length);
console.log("Predictions:", predictions.length);
console.log("Countdowns:", countdowns.length);
