package ch.epfl.biop.operetta.utils;

import java.awt.*;

public class FieldCoordinates {
    int field;
    Point coordinates;

    int width;
    int height;

    public FieldCoordinates(int field, Point point, int width, int height) {
        this.field = field;
        this.coordinates = point;
        this.width = width;
        this.height = height;
    }

    public int getField() {
        return field;
    }

    public void setField(int field) {
        this.field = field;
    }

    public Point getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Point coordinates) {
        this.coordinates = coordinates;
    }

    public int getXCoordinate() {
        return this.getCoordinates().x;
    }

    public int getYCoordinate() {
        return this.getCoordinates().y;
    }

    public int getWidth() { return this.width; }

    public int getHeight() { return this.height; }

}
