package celtech.coreUI.components;

import celtech.Lookup;
import celtech.roboxbase.configuration.PostPrintHeaterSettings;
import celtech.roboxbase.printerControl.PrinterStatus;
import celtech.roboxbase.printerControl.model.Head;
import celtech.roboxbase.printerControl.model.NozzleHeater;
import celtech.roboxbase.printerControl.model.Printer;
import celtech.roboxbase.printerControl.model.PrinterException;
import java.util.Optional;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 * Shows a warning while the printer is idle but its head or bed is still hot, and
 * offers buttons (with confirmation) to switch the head and bed heaters off.
 * When the "keep heaters on after print" preference is set, the printer's ambient LED
 * is also switched to red while the printer is hot.
 */
public class HeaterControlPane extends VBox
{

    private static final Stenographer steno = StenographerFactory.getStenographer(
            HeaterControlPane.class.getName());

    private static final int HOT_THRESHOLD_C = 50;
    private static final Color WARNING_LED_COLOUR = Color.RED;

    private final Label warningLabel = new Label();
    private final Button coolHeadButton = new Button();
    private final Button coolBedButton = new Button();
    private final CheckBox keepWarmCheckBox = new CheckBox();
    private final Timeline refreshTimeline;

    private Printer printer = null;
    private boolean ledWarningActive = false;

    public HeaterControlPane()
    {
        setAlignment(Pos.CENTER);
        setSpacing(4);
        setPadding(new Insets(4, 0, 4, 0));
        setMaxWidth(260);

        warningLabel.setStyle("-fx-text-fill: #ff661b; -fx-font-size: 12px; -fx-font-weight: bold;");
        warningLabel.setWrapText(true);
        warningLabel.setMaxWidth(260);

        coolHeadButton.setText(text("heaterControl.coolHead", "Cool Head"));
        coolBedButton.setText(text("heaterControl.coolBed", "Cool Bed"));
        coolHeadButton.setOnAction(e -> confirmAndRun(
                text("heaterControl.confirmCoolHead", "Switch off the head heater?"), () -> printer.switchAllNozzleHeatersOff()));
        coolBedButton.setOnAction(e -> confirmAndRun(
                text("heaterControl.confirmCoolBed", "Switch off the bed heater?"), () -> printer.switchBedHeaterOff()));

        HBox buttons = new HBox(8, coolHeadButton, coolBedButton);
        buttons.setAlignment(Pos.CENTER);

        keepWarmCheckBox.setText(text("heaterControl.keepWarm", "Keep warm after print"));
        keepWarmCheckBox.setStyle("-fx-text-fill: white;");
        keepWarmCheckBox.selectedProperty().bindBidirectional(
                Lookup.getUserPreferences().keepHeatersOnAfterPrintProperty());

        getChildren().addAll(warningLabel, buttons, keepWarmCheckBox);

        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        setPrinter(null);
    }

    /**
     * Looks up a translated string, falling back to the English default if the
     * language files in use don't contain the key.
     */
    private static String text(String key, String defaultText)
    {
        try
        {
            String value = Lookup.i18n(key);
            if (value != null && !value.isEmpty() && !value.equals(key) && !value.contains("heaterControl."))
            {
                return value;
            }
        } catch (RuntimeException ex)
        {
            // fall through to default
        }
        return defaultText;
    }

    public final void setPrinter(Printer newPrinter)
    {
        if (ledWarningActive)
        {
            restoreLED();
        }
        printer = newPrinter;
        refresh();
    }

    private int getMaxNozzleTemperature()
    {
        Head head = printer.headProperty().get();
        int max = 0;
        if (head != null)
        {
            for (NozzleHeater heater : head.getNozzleHeaters())
            {
                max = Math.max(max, heater.nozzleTemperatureProperty().get());
            }
        }
        return max;
    }

    private void refresh()
    {
        if (printer == null)
        {
            setVisible(false);
            setManaged(false);
            return;
        }

        int nozzleTemp = getMaxNozzleTemperature();
        int bedTemp = printer.getPrinterAncillarySystems().bedTemperatureProperty().get();
        boolean idle = printer.printerStatusProperty().get() == PrinterStatus.IDLE;
        boolean nozzleHot = nozzleTemp >= HOT_THRESHOLD_C;
        boolean bedHot = bedTemp >= HOT_THRESHOLD_C;
        boolean showWarning = idle && (nozzleHot || bedHot);

        warningLabel.setText(text("heaterControl.hotWarning", "WARNING: printer is hot!") + " "
                + text("heaterControl.head", "Head") + " " + nozzleTemp + "°C, "
                + text("heaterControl.bed", "Bed") + " " + bedTemp + "°C");
        warningLabel.setVisible(showWarning);
        warningLabel.setManaged(showWarning);

        coolHeadButton.setDisable(!(idle && nozzleHot));
        coolBedButton.setDisable(!(idle && bedHot));

        setVisible(true);
        setManaged(true);

        updateLED(showWarning && PostPrintHeaterSettings.isKeepHeatersOnAfterPrint());
    }

    private void updateLED(boolean warn)
    {
        if (warn && !ledWarningActive)
        {
            try
            {
                printer.setAmbientLEDColour(WARNING_LED_COLOUR);
                ledWarningActive = true;
            } catch (PrinterException ex)
            {
                steno.warning("Couldn't set warning LED colour: " + ex.getMessage());
            }
        } else if (!warn && ledWarningActive)
        {
            restoreLED();
        }
    }

    private void restoreLED()
    {
        ledWarningActive = false;
        if (printer != null)
        {
            Color original = printer.getPrinterIdentity().printerColourProperty().get();
            if (original != null)
            {
                try
                {
                    printer.setAmbientLEDColour(original);
                } catch (PrinterException ex)
                {
                    steno.warning("Couldn't restore LED colour: " + ex.getMessage());
                }
            }
        }
    }

    private void confirmAndRun(String message, Runnable action)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(text("heaterControl.confirmTitle", "Confirm"));
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES && printer != null)
        {
            action.run();
            refresh();
        }
    }
}
