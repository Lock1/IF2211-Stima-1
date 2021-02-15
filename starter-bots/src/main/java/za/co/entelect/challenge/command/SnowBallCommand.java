package za.co.entelect.challenge.command;

public class SnowBallCommand implements Command {

    private final int x;
    private final int y;

    public SnowBallCommand(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String render() {
        return String.format("SnowBall %d %d", x, y);
    }
}
