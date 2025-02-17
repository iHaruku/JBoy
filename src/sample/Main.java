package sample;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import goodboy.disassembler.Disassembler;
import goodboy.other.GameBoyInfo;
import goodboy.system.LCD;
import goodboy.system.GameBoy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class Main extends Application {
    private Stage stage;

    private ListView<String> listView = new ListView<>();
    private PixelWriter pixelWriter;
    private PixelFormat<ByteBuffer> pixelFormat;

    private Thread gameThread;
    private GameBoy gameBoy;

    private GameBoyInfo gbInfo;

    private Stage debugWindow;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("GoodBoy");

        VBox vbox = new VBox();
        MenuBar menuBar = createMenuBar();
        WritableImage image = new WritableImage(LCD.WIDTH, LCD.HEIGHT);
        ImageView imageView = new ImageView(image);

        // TODO: get rid of this ghetto scaling
        imageView.setFitWidth((double) LCD.WIDTH * 2);
        imageView.setFitHeight((double) LCD.HEIGHT * 2);
        imageView.snapshot(null, null);

        this.pixelWriter = image.getPixelWriter();
        this.pixelFormat = PixelFormat.getByteBgraInstance();

        vbox.getChildren().add(menuBar);
        vbox.getChildren().add(imageView);

        // TODO: gotta figure out how to size the scene
        Scene scene = new Scene(vbox, (LCD.WIDTH * 2), (LCD.HEIGHT * 2) + 29);

        primaryStage.setScene(scene);
        primaryStage.setX((Screen.getPrimary().getBounds().getWidth() / 2) - LCD.WIDTH);
        primaryStage.setY((Screen.getPrimary().getBounds().getHeight() / 2) - (LCD.HEIGHT * 2));
        primaryStage.show();
        this.gameBoy = new GameBoy();
        this.gameBoy.getLCD().setDrawFunction(this::drawImage);

        this.stage.setOnCloseRequest(x -> {
            // close the debug window if it's open.
            if(this.debugWindow != null) {
                this.debugWindow.close();
            }

            // kill the game thread
            if(this.gameThread != null) {
                this.gameThread.interrupt();
            }
        });

//        this.openDebugWindow();
    }

    @Override
    public void stop() throws Exception {
        this.dispose();

        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private MenuBar createMenuBar() {
        MenuBar mbMenu = new MenuBar();

        mbMenu.getMenus().add(createFileMenu());
        mbMenu.getMenus().add(createActionsMenu());

        return mbMenu;
    }

    private Menu createFileMenu() {
        Menu mFile = new Menu("File");
        MenuItem miLoadRom = new MenuItem("Load Rom...");

        miLoadRom.setOnAction(x -> {
            FileChooser chooser = new FileChooser();
            chooser.setInitialDirectory(new File("resources/roms/tests/mooneye"));
            File file = chooser.showOpenDialog(this.stage);

            if(file != null) {
                this.loadRom(file);
            }
        });

        mFile.getItems().add(miLoadRom);

        return mFile;
    }

    private Menu createActionsMenu() {
        Menu mActions = new Menu("Actions");
        MenuItem miDisassemble = new MenuItem("Disassemble...");
        MenuItem miDebug = new MenuItem("Show debugger");

        miDisassemble.setOnAction(x -> {
            FileChooser chooser = new FileChooser();
            File file = chooser.showOpenDialog(this.stage);

            if(file != null) {
                this.disassemble(file);
            }
        });

        miDebug.setOnAction(x -> {
            this.openDebugWindow();
        });

        mActions.getItems().add(miDisassemble);
        mActions.getItems().add(miDebug);

        return mActions;
    }

    private void dispose() {
        if(this.gameThread != null) {
            this.gameThread.interrupt();
        }
    }

    private void loadRom(File file) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));
            int[] rom = new int[bytes.length];

            for(int i = 0; i < bytes.length; i++) {
                rom[i] = bytes[i] & 0xFF;
            }

            this.gameBoy.loadROM(rom);

            this.gameThread = new Thread(this.gameBoy);
            this.gameThread.start();

            System.out.println(this.gameBoy.getCartridgeInfo());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Void drawImage(byte[] data) {
        Platform.runLater(() -> this.pixelWriter.setPixels(
                0,
                0,
                LCD.WIDTH,
                LCD.HEIGHT,
                this.pixelFormat,
                data,
                0,
                LCD.WIDTH * 4
        ));

        return null;
    }

    private void disassemble(File file) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));
            int[] rom = new int[bytes.length];

            for(int i = 0; i < bytes.length; i++) {
                rom[i] = bytes[i] & 0xFF;
            }

            Disassembler disassembler = new Disassembler(rom);
            disassembler.disassemble();

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    HashMap<Integer, String> list = disassembler.getDisassemblyList();

                    list.forEach((index, code) -> {
                        listView.getItems().add(code);
                    });

                    return null;
                }
            };

            new Thread(task).start();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void openDebugWindow() {
        final int height = 400;
        final int width = 1000;
        DebugWindow dbgWindow = new DebugWindow(this.gameBoy);

        dbgWindow.createRegisters();
        dbgWindow.createFlagCheckboxes();
        dbgWindow.createTimer();
        dbgWindow.createMemoryControls();
        dbgWindow.createCpuControls();
        dbgWindow.createBreakpointControls();
        dbgWindow.createDisassembly();

        Scene debugScene = new Scene(dbgWindow.getLayout());
        this.debugWindow = new Stage();

        this.debugWindow.setX((Screen.getPrimary().getBounds().getWidth() / 2) - (width / 2f));
        this.debugWindow.setY((Screen.getPrimary().getBounds().getHeight() / 2) + (LCD.HEIGHT));

        this.debugWindow.setOnCloseRequest(windowEvent -> {
            this.gameBoy.setIsDebugging(false);

            if(this.gameThread != null) {
                this.gameThread.interrupt();
            }
        });

        this.debugWindow.setTitle("Debugger");
        this.debugWindow.setScene(debugScene);
        this.debugWindow.setHeight(height);
        this.debugWindow.setWidth(width);

        debugScene.setOnKeyPressed(event -> {
            if(event.getCode() == KeyCode.F7) {
                dbgWindow.tick();
            }
        });

        this.gameBoy.setIsDebugging(true);

        this.gbInfo = this.gameBoy.getInfo();
        this.gbInfo.setDebugUpdateFunction(dbgWindow::updateWindow);

        this.gameBoy.reset();
        this.debugWindow.show();
    }
}
