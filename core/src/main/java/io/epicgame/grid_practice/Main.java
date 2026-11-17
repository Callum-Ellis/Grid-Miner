package io.epicgame.grid_practice;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.net.HttpStatus;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;

public class Main implements ApplicationListener {
    private ShapeRenderer sr;
    private BitmapFont font;
    private SpriteBatch batch;
    private Music goldCollectedSound;
    private float time = 15f;
    private int score;
    private int highScore;
    private boolean gameOverHandled;
    private boolean gameStarted;
    private boolean usernamePromptActive;
    private String username;
    private boolean hasUserHighScore;
    private int userHighScore;
    private final String[] topUsernames = new String[3];
    private final int[] topScores = new int[3];
    private int topScoreCount;
    private final StringBuilder usernameInput = new StringBuilder();
    private String firebaseStatus;
    private com.badlogic.gdx.Preferences preferences;

    private static final String FIREBASE_PROJECT_ID = "portfolio-d65d0";
    private static final String FIREBASE_API_KEY = "AIzaSyDhSBYpnNQq6vVq6UhqN5j1gUUz7SycJc";
    private static final String FIREBASE_COLLECTION = "EpicGame2";

    // Grid representation: 0 = Empty, 1 = Player, 2 = Coin
    private final int[][] grid = {
        {0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0}
    };
    private int playerRow;
    private int playerCol;

    @Override
    public void create() {

        sr = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.0f);
        goldCollectedSound = Gdx.audio.newMusic(Gdx.files.internal("wii-download-assistant-sound.mp3"));
        preferences = Gdx.app.getPreferences("grid-practice");
        highScore = preferences.getInteger("highScore", 0);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
                if (!gameStarted && usernamePromptActive
                    && character >= 32 && character != 127 && usernameInput.length() < 24) {
                    usernameInput.append(character);
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (!gameStarted && usernamePromptActive) {
                    if (keycode == Input.Keys.BACKSPACE && !usernameInput.isEmpty()) {
                        usernameInput.deleteCharAt(usernameInput.length() - 1);
                        return true;
                    }
                    if (keycode == Input.Keys.ENTER) {
                        submitUsername();
                        return true;
                    }
                }
                return false;
            }
        });

        int randomCol2 = MathUtils.random(0, grid[0].length - 1);
        int randomRow2 = MathUtils.random(0, grid.length - 1);
        grid[playerRow][playerCol] = 0;
        grid[randomRow2][randomCol2] = 1;
        playerRow = randomRow2;
        playerCol = randomCol2;

        spawnGoldBlock();
        requestUsername();
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!gameStarted) {
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(Color.DARK_GRAY);
            sr.rect(80, 205, Gdx.graphics.getWidth() - 160, 48);
            sr.end();
            batch.begin();
            font.setColor(Color.WHITE);
            drawCentered("Grid Practice", 340);
            drawCentered("Enter your username to start", 285);
            drawCentered("Name: " + usernameInput + "_", 235);
            drawCentered("Type with your keyboard, then press Enter", 175);
            if (firebaseStatus != null) {
                drawCentered(firebaseStatus, 145);
            }
            batch.end();
        } else if (time >= 0) {
            time -= Gdx.graphics.getDeltaTime();
            int tempRow = playerRow;
            int tempCol = playerCol;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) tempRow--;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) tempRow++;
            if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) tempCol--;
            if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) tempCol++;

            boolean collectedGold = false;
            if (tempRow >= 0 && tempRow < grid.length
                && tempCol >= 0 && tempCol < grid[0].length
                && (tempRow != playerRow || tempCol != playerCol)) {
                if (grid[tempRow][tempCol] == 2) {
                    score++;
                    grid[tempRow][tempCol] = 0;
                    goldCollectedSound.stop();
                    goldCollectedSound.play();
                    collectedGold = true;
                }
                grid[playerRow][playerCol] = 0;
                grid[tempRow][tempCol] = 1;
                playerRow = tempRow;
                playerCol = tempCol;
                if (collectedGold) {
                    spawnGoldBlock();
                }
            }

            sr.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < grid.length; i++) {
                for (int j = 0; j < grid[i].length; j++) {
                    if (grid[i][j] == 1) {
                        sr.setColor(Color.FIREBRICK);
                    } else if (grid[i][j] == 2) {
                        sr.setColor(Color.GOLD);
                    } else {
                        sr.setColor(Color.DARK_GRAY);
                    }
                    sr.rect(100 + j * 80, 50 + (4 - i) * 80, 76, 76);
                }
            }
            sr.end();
            batch.begin();
            font.setColor(Color.WHITE);
            String hud = "Score: " + score + " | Time: " + Math.max(0, (int) time);
            if (hasUserHighScore) {
                hud += " | High score: " + userHighScore;
            }
            font.draw(batch, hud, 10, Gdx.graphics.getHeight() - 10);
            batch.end();
        } else {
            if (!gameOverHandled) {
                highScore = Math.max(highScore, score);
                preferences.putInteger("highScore", highScore);
                preferences.flush();
                gameOverHandled = true;
                saveScoreToFirebase(username, score);
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.R)
                || isRestartButtonClicked()) {
                restartGame();
            }

            batch.begin();
            font.setColor(Color.WHITE);
            drawCentered("Time's up!", 300);
            drawCentered("Score: " + score, 255);
            if (hasUserHighScore) {
                drawCentered("High score: " + userHighScore, 215);
            }
            drawCentered("Player: " + username, 175);
            if (topScoreCount > 0) {
                drawCentered("Top scores", 135);
                for (int i = 0; i < topScoreCount; i++) {
                    drawCentered((i + 1) + ". " + topUsernames[i] + " - " + topScores[i],
                        105 - i * 25);
                }
            }
            if (firebaseStatus != null) {
                drawCentered(firebaseStatus, topScoreCount > 0 ? 145 : 125);
            }
            batch.end();

            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(Color.FIREBRICK);
            sr.rect(220, 25, 200, 45);
            sr.end();
            batch.begin();
            font.setColor(Color.WHITE);
            drawCentered("Press R or Click to Restart", 55);
            batch.end();
        }
    }

    @Override
    public void resize(int w, int h) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        sr.dispose();
        batch.dispose();
        font.dispose();
        goldCollectedSound.dispose();
    }

    private void spawnGoldBlock() {
        int blockCount = MathUtils.random(1, 20) == 1 ? 2 : 1;

        for (int i = 0; i < blockCount; i++) {
            int goldRow;
            int goldCol;
            do {
                goldRow = MathUtils.random(0, grid.length - 1);
                goldCol = MathUtils.random(0, grid[0].length - 1);
            } while (grid[goldRow][goldCol] != 0);
            grid[goldRow][goldCol] = 2;
        }
    }

    private boolean isRestartButtonClicked() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return false;
        }
        float screenY = Gdx.graphics.getHeight() - Gdx.input.getY();
        return Gdx.input.getX() >= 220 && Gdx.input.getX() <= 420
            && screenY >= 25 && screenY <= 70;
    }

    private void restartGame() {
        time = 15f;
        score = 0;
        gameOverHandled = false;
        gameStarted = true;

        for (int row = 0; row < grid.length; row++) {
            Arrays.fill(grid[row], 0);
        }

        playerRow = MathUtils.random(0, grid.length - 1);
        playerCol = MathUtils.random(0, grid[0].length - 1);
        grid[playerRow][playerCol] = 1;
        spawnGoldBlock();

        hasUserHighScore = false;
        firebaseStatus = "Game restarted! Try hard Gamer!";
        getUserHighScore(username);
    }

    private void requestUsername() {
        usernamePromptActive = true;
        firebaseStatus = "Enter your username to start GAMING.";
        usernameInput.setLength(0);
    }

    private void submitUsername() {
        String trimmedUsername = usernameInput.toString().trim();
        if (trimmedUsername.isEmpty()) {
            firebaseStatus = "A username is required dork.";
            return;
        }
        username = trimmedUsername;
        usernamePromptActive = false;
        hasUserHighScore = false;
        gameStarted = true;
        firebaseStatus = "Game starting!...";
        getUserHighScore(trimmedUsername);
    }

    private void drawCentered(String text, float y) {
        GlyphLayout layout = new GlyphLayout(font, text);
        float x = Math.max(10, (Gdx.graphics.getWidth() - layout.width) / 2f);
        font.draw(batch, text, x, y);
    }

    private void saveScoreToFirebase(String username, int scoreToSave) {
        firebaseStatus = "Saving score for elite Gamer...";
        String url = "https://firestore.googleapis.com/v1/projects/" + FIREBASE_PROJECT_ID
            + "/databases/(default)/documents/" + FIREBASE_COLLECTION
            + "?documentId=" + encodeDocumentId(username)
            + "&key=" + FIREBASE_API_KEY;

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url);
        request.setHeader("Content-Type", "application/json");
        request.setContent("{\"fields\":{\"username\":{\"stringValue\":\""
            + escapeJson(username) + "\"},\"highscore\":{\"integerValue\":\""
            + scoreToSave + "\"}}}");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                HttpStatus status = httpResponse.getStatus();
                if (status.getStatusCode() == 200 || status.getStatusCode() == 201) {
                    firebaseStatus = "Score saved for " + username + ".";
                    Gdx.app.log("Firebase", "Score saved for " + username + ".");
                    getTopScores();
                } else if (status.getStatusCode() == 409) {
                    updateExistingUserScore(username, scoreToSave);
                } else {
                    firebaseStatus = "Could not save score (" + status.getStatusCode() + ").";
                    Gdx.app.log("Firebase", "Save Error: " + status.getStatusCode()
                        + " - " + httpResponse.getResultAsString());
                }
            }

            @Override
            public void failed(Throwable t) {
                firebaseStatus = "Could not connect to Firebase.";
                Gdx.app.log("Firebase", "Connection Error", t);
            }

            @Override
            public void cancelled() {
                firebaseStatus = "Score save was canceled. Bad Gamer.";
                Gdx.app.log("Firebase", "Canceled Request");
            }
        });
    }

    private void updateExistingUserScore(String username, int scoreToSave) {
        String url = "https://firestore.googleapis.com/v1/projects/" + FIREBASE_PROJECT_ID
            + "/databases/(default)/documents/" + FIREBASE_COLLECTION + "/"
            + encodeDocumentId(username)
            + "?updateMask.fieldPaths=username&updateMask.fieldPaths=highscore&key="
            + FIREBASE_API_KEY;
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("X-HTTP-Method-Override", "PATCH");
        request.setContent("{\"fields\":{\"username\":{\"stringValue\":\""
            + escapeJson(username) + "\"},\"highscore\":{\"integerValue\":\""
            + scoreToSave + "\"}}}");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                if (httpResponse.getStatus().getStatusCode() == 200) {
                    firebaseStatus = "Score saved for " + username + ".";
                    Gdx.app.log("Firebase", "Score updated for " + username + ".");
                    getTopScores();
                } else {
                    firebaseStatus = "Could not update score ("
                        + httpResponse.getStatus().getStatusCode() + ").";
                    Gdx.app.log("Firebase", "Update Error: "
                        + httpResponse.getStatus().getStatusCode());
                }
            }

            @Override
            public void failed(Throwable t) {
                firebaseStatus = "Could not connect to Firebase.";
                Gdx.app.log("Firebase", "Update connection error", t);
            }

            @Override
            public void cancelled() {
                firebaseStatus = "Score update was canceled.";
                Gdx.app.log("Firebase", "Update canceled");
            }
        });
    }

    private void getUserHighScore(String usernameToFind) {
        String url = "https://firestore.googleapis.com/v1/projects/" + FIREBASE_PROJECT_ID
            + "/databases/(default)/documents:runQuery?key=" + FIREBASE_API_KEY;
        String requestBody = "{\"structuredQuery\":{\"from\":[{\"collectionId\":\""
            + FIREBASE_COLLECTION + "\"}],\"where\":{\"fieldFilter\":{\"field\":"
            + "{\"fieldPath\":\"username\"},\"op\":\"EQUAL\",\"value\":{\"stringValue\":\""
            + escapeJson(usernameToFind) + "\"}}},\"limit\":1}}";

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url);
        request.setHeader("Content-Type", "application/json");
        request.setContent(requestBody);

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                HttpStatus status = httpResponse.getStatus();
                if (status.getStatusCode() != 200) {
                    Gdx.app.log("Firebase", "High score lookup error: " + status.getStatusCode()
                        + " - " + httpResponse.getResultAsString());
                    return;
                }

                JsonValue results = new JsonReader().parse(httpResponse.getResultAsString());
                if (results.isArray() && results.size > 0) {
                    JsonValue document = results.get(0).get("document");
                    if (document != null && document.has("fields")
                        && document.get("fields").has("highscore")) {
                        String scoreText = document.get("fields").get("highscore")
                            .getString("integerValue", "");
                        try {
                            userHighScore = Integer.parseInt(scoreText);
                            hasUserHighScore = true;
                        } catch (NumberFormatException exception) {
                            Gdx.app.log("Firebase", "Invalid high score returned: " + scoreText);
                        }
                    }
                }
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.log("Firebase", "High score lookup connection error", t);
            }

            @Override
            public void cancelled() {
                Gdx.app.log("Firebase", "High score lookup canceled");
            }
        });
    }

    private void getTopScores() {
        String url = "https://firestore.googleapis.com/v1/projects/" + FIREBASE_PROJECT_ID
            + "/databases/(default)/documents:runQuery?key=" + FIREBASE_API_KEY;
        String requestBody = "{\"structuredQuery\":{\"from\":[{\"collectionId\":\""
            + FIREBASE_COLLECTION + "\"}],\"orderBy\":[{\"field\":{\"fieldPath\":\"highscore\"},"
            + "\"direction\":\"DESCENDING\"}],\"limit\":3}}";

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url);
        request.setHeader("Content-Type", "application/json");
        request.setContent(requestBody);

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                HttpStatus status = httpResponse.getStatus();
                if (status.getStatusCode() != 200) {
                    Gdx.app.log("Firebase", "Top score lookup error: " + status.getStatusCode()
                        + " - " + httpResponse.getResultAsString());
                    return;
                }

                JsonValue results = new JsonReader().parse(httpResponse.getResultAsString());
                topScoreCount = 0;
                for (JsonValue result = results.child; result != null && topScoreCount < 3;
                     result = result.next) {
                    JsonValue document = result.get("document");
                    if (document == null || !document.has("fields")) {
                        continue;
                    }
                    JsonValue fields = document.get("fields");
                    String scoreText = fields.get("highscore").getString("integerValue", "");
                    String name = fields.get("username").getString("stringValue", "");
                    try {
                        topScores[topScoreCount] = Integer.parseInt(scoreText);
                        topUsernames[topScoreCount] = name;
                        topScoreCount++;
                    } catch (NumberFormatException exception) {
                        Gdx.app.log("Firebase", "Invalid top score returned: " + scoreText);
                    }
                }
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.log("Firebase", "Top score lookup connection error", t);
            }

            @Override
            public void cancelled() {
                Gdx.app.log("Firebase", "Top score lookup canceled");
            }
        });
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String encodeDocumentId(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is not available", exception);
        }
    }

}
