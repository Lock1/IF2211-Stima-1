package za.co.entelect.challenge.command;

public class BananaCommand implements Command {

    private final int x;
    private final int y;

    public BananaCommand(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String render() {
        return String.format("Banana %d %d", x, y);
    }
}
