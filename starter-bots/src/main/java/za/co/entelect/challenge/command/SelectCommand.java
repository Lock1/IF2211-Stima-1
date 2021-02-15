package za.co.entelect.challenge.command;

public class SelectCommand implements Command {

    private int id;
    private Command command;

    public DigCommand(int id, Command command) {
        this.id = id;
        this.command = command;
    }

    @Override
    public String render() {
        return String.format("select {%d};{%s}", id, command);
    }
}