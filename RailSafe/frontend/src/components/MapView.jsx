import React, { useEffect, useRef } from 'react';
import L from 'leaflet';
import { gateStatusColor, gateStatusLabel, ROUTE_COLORS } from '../status.js';

function escapeHtml(value) {
  return String(value === null || value === undefined ? '' : value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export default function MapView({ gate, start, destination, routes, selectedRouteId, gateStatus }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const layersRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return undefined;

    const map = L.map(containerRef.current, {
      zoomControl: true,
      scrollWheelZoom: false
    }).setView([gate.latitude, gate.longitude], 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

    layersRef.current = L.layerGroup().addTo(map);
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
      layersRef.current = null;
    };
  }, [gate.latitude, gate.longitude]);

  useEffect(() => {
    const map = mapRef.current;
    const layers = layersRef.current;
    if (!map || !layers) return;

    layers.clearLayers();
    const bounds = [];

    if (gate) {
      const gatePoint = [gate.latitude, gate.longitude];
      bounds.push(gatePoint);
      const statusLabel = gateStatusLabel(gateStatus);
      L.circleMarker(gatePoint, {
        radius: 11,
        color: '#ffffff',
        weight: 3,
        fillColor: gateStatusColor(gateStatus),
        fillOpacity: 1
      })
        .bindPopup(
          `<strong>${escapeHtml(gate.name || gate.gateName)}</strong><br/>` +
          `${escapeHtml(gate.gateCode || gate.gateId)}<br/>Status: ${escapeHtml(statusLabel)}`
        )
        .addTo(layers);
    }

    if (start) {
      bounds.push([start.lat, start.lng]);
      L.circleMarker([start.lat, start.lng], {
        radius: 7, color: '#ffffff', weight: 2, fillColor: '#0f766e', fillOpacity: 1
      }).bindPopup('Start').addTo(layers);
    }

    if (destination) {
      bounds.push([destination.lat, destination.lng]);
      L.circleMarker([destination.lat, destination.lng], {
        radius: 7, color: '#ffffff', weight: 2, fillColor: '#7c3aed', fillOpacity: 1
      }).bindPopup('Destination').addTo(layers);
    }

    (routes || []).forEach((route, index) => {
      if (!route.geometry || route.geometry.length < 2) return;
      const selected = route.id === selectedRouteId;
      const color = route.color || ROUTE_COLORS[index % ROUTE_COLORS.length];
      route.geometry.forEach((point) => bounds.push([point.lat, point.lng]));
      L.polyline(route.geometry.map((point) => [point.lat, point.lng]), {
        color,
        weight: selected ? 7 : 4,
        opacity: selected ? 0.95 : 0.75,
        dashArray: selected ? null : '7 7'
      })
        .bindPopup(`<strong>${escapeHtml(route.label)}</strong><br/>${route.distanceKm} km · ${route.durationMinutes} min`)
        .addTo(layers);
    });

    if (bounds.length === 1) {
      map.setView(bounds[0], 14);
    } else if (bounds.length > 1) {
      map.fitBounds(bounds, { padding: [32, 32], maxZoom: 15 });
    }
    window.setTimeout(() => map.invalidateSize(), 0);
  }, [gate, start, destination, routes, selectedRouteId, gateStatus]);

  return (
    <div
      ref={containerRef}
      className="map-canvas"
      role="img"
      aria-label={`OpenStreetMap showing ${gate?.name || gate?.gateName || 'the selected crossing'} and calculated routes`}
    />
  );
}
