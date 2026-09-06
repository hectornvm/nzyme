package app.nzyme.core.database.generic;

public enum TwoColumnHistogramOrderColumn {

    KEY("key"),
    VALUE("value");

    private final String columnName;

    TwoColumnHistogramOrderColumn(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() {
        return columnName;
    }

}
