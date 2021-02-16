package za.co.entelect.challenge.entities;

import com.google.gson.annotations.SerializedName;

public class Position {
    Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @SerializedName("x")
    public int x;

    @SerializedName("y")
    public int y;
}
