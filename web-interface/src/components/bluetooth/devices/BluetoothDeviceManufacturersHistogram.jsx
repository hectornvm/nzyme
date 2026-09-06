import React, {useContext, useEffect, useState} from "react";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import {TapContext} from "../../../App";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import BluetoothService from "../../../services/BluetoothService";
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../BluetoothDevicesFilterFields";

const bluetoothService = new BluetoothService();

export default function BluetoothDeviceManufacturersHistogram({timeRange, filters, setFilters, revision}) {

  const tapContext = useContext(TapContext);

  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    bluetoothService.getDeviceManufacturersHistogram(setHistogram, timeRange, limit, 0, filters, selectedTaps);
  }, [selectedTaps, limit, timeRange, filters, revision]);

  if (!histogram) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No devices with manufacturer data recorded.
      </div>
    )
  }

  return <TwoColumnHistogram data={histogram}
                             columnTitles={["Manufacturer", "Device Count"]}
                             columnFilterElements={[
                               {field: "manufacturer_names", fields: BLUETOOTH_DEVICES_FILTER_FIELDS, setFilters: setFilters},
                               null, null
                             ]}
                             limit={limit}
                             setLimit={setLimit} />

}