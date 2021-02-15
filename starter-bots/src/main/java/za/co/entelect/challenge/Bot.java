package za.co.entelect.challenge;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.CellType;
import za.co.entelect.challenge.enums.Direction;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

public class Bot {

    private GameState gameState;
    private Opponent opponent;
    private MyWorm currentWorm;

    public Bot(GameState gameState) {
        this.gameState = gameState;
        this.opponent = gameState.opponents[0];
        this.currentWorm = getCurrentWorm(gameState);
    }

    // Ambil worm sekarang
    private MyWorm getCurrentWorm(GameState gameState) {
        return Arrays.stream(gameState.myPlayer.worms)
                .filter(myWorm -> myWorm.id == gameState.currentWormId)
                .findFirst()
                .get();
    }

    public Command run() {

        Worm enemyWorm = getFirstWormInRange();

        if (enemyWorm != null) {
            Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
            return new ShootCommand(direction);
        }

        Direction moveDirection = nearestEnemyDirection(currentWorm.position);
        List<Cell> surroundingBlocks = getSurroundingCells(currentWorm.position.x, currentWorm.position.y);

        int xDirection = currentWorm.position.x + moveDirection.x;
        int yDirection = currentWorm.position.y + moveDirection.y;

        for (int i = 0; i < surroundingBlocks.size(); i++) {
            Cell block = surroundingBlocks.get(i);

            if (block.x == xDirection && block.y == yDirection) {
                if (block.type == CellType.AIR) {
                    return new MoveCommand(xDirection, yDirection);
                } else if (block.type == CellType.DIRT) {
                    return new DigCommand(xDirection, yDirection);
                }
            }
        }

        return new DoNothingCommand();
    }

    private Worm getFirstWormInRange() {

        Set<String> cells = constructFireDirectionLines(currentWorm.weapon.range)
                .stream()
                .flatMap(Collection::stream)
                .map(cell -> String.format("%d_%d", cell.x, cell.y))
                .collect(Collectors.toSet());

        for (Worm enemyWorm : opponent.worms) {
            String enemyPosition = String.format("%d_%d", enemyWorm.position.x, enemyWorm.position.y);
            if (cells.contains(enemyPosition)) {
                return enemyWorm;
            }
        }

        return null;
    }

    private Direction nearestEnemyDirection(Position currentWorm) {
        ArrayList<Position> enemyPosition = new ArrayList<>();

        for (int i = 0; i < gameState.opponents[0].worms.length; i++) {
            enemyPosition.add(gameState.opponents[0].worms[i].position);
        }

        int minimalDistance = euclideanDistance(currentWorm.x, currentWorm.y, enemyPosition.get(0).x, enemyPosition.get(0).y);
        int minimalIndex = 0;

        for (int i = 1; i < enemyPosition.size(); i++) {
            int temp = euclideanDistance(currentWorm.x, currentWorm.y, enemyPosition.get(i).x, enemyPosition.get(i).y);

            if (temp < minimalDistance) {
                minimalIndex = i;
            }
        }

        return resolveDirection(currentWorm, enemyPosition.get(minimalIndex));
    }

    private List<List<Cell>> constructFireDirectionLines(int range) {
        List<List<Cell>> directionLines = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            List<Cell> directionLine = new ArrayList<>();
            for (int directionMultiplier = 1; directionMultiplier <= range; directionMultiplier++) {

                int coordinateX = currentWorm.position.x + (directionMultiplier * direction.x);
                int coordinateY = currentWorm.position.y + (directionMultiplier * direction.y);

                if (!isValidCoordinate(coordinateX, coordinateY)) {
                    break;
                }

                if (euclideanDistance(currentWorm.position.x, currentWorm.position.y, coordinateX, coordinateY) > range) {
                    break;
                }

                Cell cell = gameState.map[coordinateY][coordinateX];
                if (cell.type != CellType.AIR) {
                    break;
                }

                directionLine.add(cell);
            }
            directionLines.add(directionLine);
        }

        return directionLines;
    }

    private List<Cell> getSurroundingCells(int x, int y) {
        ArrayList<Cell> cells = new ArrayList<>();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                // Don't include the current position
                if (i != x && j != y && isValidCoordinate(i, j)) {
                    cells.add(gameState.map[j][i]);
                }
            }
        }

        return cells;
    }

    private int euclideanDistance(int aX, int aY, int bX, int bY) {
        return (int) (Math.sqrt(Math.pow(aX - bX, 2) + Math.pow(aY - bY, 2)));
    }

    private boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < gameState.mapSize
                && y >= 0 && y < gameState.mapSize;
    }

    private Direction resolveDirection(Position a, Position b) {
        StringBuilder builder = new StringBuilder();

        int verticalComponent = b.y - a.y;
        int horizontalComponent = b.x - a.x;

        if (verticalComponent < 0) {
            builder.append('N');
        } else if (verticalComponent > 0) {
            builder.append('S');
        }

        if (horizontalComponent < 0) {
            builder.append('W');
        } else if (horizontalComponent > 0) {
            builder.append('E');
        }

        return Direction.valueOf(builder.toString());
    }
}
