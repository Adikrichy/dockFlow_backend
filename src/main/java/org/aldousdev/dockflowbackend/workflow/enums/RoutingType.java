package org.aldousdev.dockflowbackend.workflow.enums;

public enum RoutingType {
    ON_APPROVE("onApprove"),
    ON_REJECT("onReject"),
    ON_TIMEOUT("onTimeout");

    private final String xmlValue;

    RoutingType(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    public String getXmlValue() {
        return xmlValue;
    }

    public static RoutingType fromXmlValue(String value) {
        for (RoutingType type : values()) {
            if (type.xmlValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown routing type: " + value);
    }
}
