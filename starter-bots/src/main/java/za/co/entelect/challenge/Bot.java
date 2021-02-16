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

        // DEBUG
        // if (currentWorm.bananaBomb != null && currentWorm.bananaBomb.count > 0)
        //     return new BananaCommand(currentWorm.position.x+3,currentWorm.position.y);
        // if (currentWorm.snowball != null && currentWorm.snowball.count > 0)
        //     return new SnowBallCommand(currentWorm.position.x+1,currentWorm.position.y);

        Worm enemyWorm = getFirstWormInRange();
        Worm enemyWormB = getSpecialRange();

        //Jika ada enemy worm di range special (banana/snowball), panggil special skill
        if (enemyWormB != null){
            if (canDoBanana(currentWorm)){
                return new BananaCommand(enemyWormB.position.x, enemyWormB.position.y);
            } else if (canDoSnowball(currentWorm)){
                return new SnowBallCommand(enemyWormB.position.x, enemyWormB.position.y);
            }
        }

        // Jika ada sebuah enemy worm, panggil shoot command
        if (enemyWorm != null) {
            Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
            return new ShootCommand(direction);
        }

        //Select worm secara paksa yang tidak sesuai dengan urutan id
        if (gameState.myPlayer.remainingWormSelections > 0) {
            for (int i = 0; i < gameState.myPlayer.worms.length; i++) {
                Worm worm = gameState.myPlayer.worms[i];

                if (worm != currentWorm && currentWorm.health > 0) {
                    Worm shootableWorm = getFirstWormInRange();

                    if (shootableWorm != null) {
                        Direction direction = resolveDirection(worm.position, shootableWorm.position);
                        return new SelectCommand(worm.id, new ShootCommand(direction));
                    }
                }
            }
        }

        // Arah gerak
        Direction moveDirection = nearestEnemyDirection(currentWorm.position);

        // Tujuan
        int xDestination = currentWorm.position.x + moveDirection.x;
        int yDestination = currentWorm.position.y + moveDirection.y;

        // Tentukan apakah dig atau gerak
        return moveOrDigToCell(xDestination, yDestination, currentWorm.position);

    }

    private Command moveOrDigToCell(int x, int y, Position currentPosition) {
        List<Cell> surroundingBlocks = getSurroundingCells(currentPosition.x, currentPosition.y);

        for (int i = 0; i < surroundingBlocks.size(); i++) {
            Cell block = surroundingBlocks.get(i);

            if (block.x == x && block.y == y) {
                if (block.type == CellType.AIR) {
                    return new MoveCommand(x,y);
                } else if (block.type == CellType.DIRT) {
                    return new DigCommand(x,y);
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
            if (cells.contains(enemyPosition) && enemyWorm.health>0) {
                return enemyWorm;
            }
        }

        return null;
    }

    private Worm getSpecialRange() {

        Set<String> cells = constructFireDirectionLines(5)
                .stream()
                .flatMap(Collection::stream)
                .map(cell -> String.format("%d_%d", cell.x, cell.y))
                .collect(Collectors.toSet());

        for (Worm enemyWorm : opponent.worms) {
            String enemyPosition = String.format("%d_%d", enemyWorm.position.x, enemyWorm.position.y);
            if (cells.contains(enemyPosition) && enemyWorm.health>0) {
                return enemyWorm;
            }
        }

        return null;
    }

    private Direction nearestEnemyDirection(Position currentWorm) {
        ArrayList<Position> enemyPosition = new ArrayList<>();

        // Iterasi melalui array worm musuh
        for (int i = 0; i < this.opponent.worms.length; i++) {
            enemyPosition.add(this.opponent.worms[i].position);
        }

        int minimalDistance = euclideanDistance(currentWorm.x, currentWorm.y, enemyPosition.get(0).x, enemyPosition.get(0).y);
        int minimalIndex = 0;

        // Cari worm terdekat
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

    private boolean canDoBanana(Worm currentWorm){
        return currentWorm.id == 2;
    }

    private boolean canDoSnowball(Worm currentWorm){
        return currentWorm.id == 3;
    }

    // private boolean canShoot(List<List<Cell>> range){
    //     for
    // }

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
