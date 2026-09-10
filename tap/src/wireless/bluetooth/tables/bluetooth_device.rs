use std::collections::HashMap;
use chrono::{DateTime, Utc};
use crate::wireless::bluetooth::detection::device_tagger::TagValue;

#[derive(Debug, Clone)]
pub struct BluetoothDevice {
    pub mac: String,
    pub name: Option<String>,
    pub rssi: Option<i16>,
    pub company_id: Option<u16>,
    pub address_type: Option<String>,
    pub alias: String,
    pub class: Option<u32>,
    pub appearance: Option<u32>,
    pub modalias: Option<String>,
    pub tx_power: Option<i16>,
    pub manufacturer_data: Option<Vec<u8>>,
    pub uuids: Option<Vec<String>>,
    // Service UUID -> payload bytes (BlueZ ServiceData a{sv}). Was UUID-keys-only before Plan H
    // Pillar 2b.
    pub service_data: Option<HashMap<String, Vec<u8>>>,
    pub device: String,
    pub transport: String,
    pub tags: Option<HashMap<String, HashMap<String, TagValue>>>,
    pub last_seen: DateTime<Utc>
}