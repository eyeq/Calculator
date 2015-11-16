package calculator;

import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    private Calculator calculator = new Calculator();

    private int caretPosition = 0;

    @FXML
    private TextField preDisplay;
    @FXML
    private TextField display;

    @Override
    public void initialize(URL url, ResourceBundle resourcebundle) {
        this.setMemory(BigDecimal.ZERO);
        this.setAnswer(BigDecimal.ZERO);
        display.caretPositionProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if(display.isFocused()) {
                caretPosition = display.getCaretPosition();
            }
        });
    }

    @FXML
    public void onPreDisplayClicked(MouseEvent event) {
        display.requestFocus();
    }

    @FXML
    public void onKeyTyped(KeyEvent event) {
        if(event.getCharacter().equals("=")) {
            event.consume();
            this.action();
        }
    }

    @FXML
    public void onButtonClicked(ActionEvent event) {
        display.requestFocus();

        Button button = (Button) event.getSource();
        String key = button.getText();
        switch(key) {
        case "MC":
            this.setMemory(BigDecimal.ZERO);
            break;
        case "BS":
            this.backSpace();
            break;
        case "CE":
            display.setText("");
            break;
        case "CA":
            this.setMemory(BigDecimal.ZERO);
            this.setAnswer(BigDecimal.ZERO);
            preDisplay.setText("");
            display.setText("");
            break;
        case "MS":
            this.action();
            this.setMemory(this.getAnswer());
            break;
        case "M-":
            this.action();
            this.setMemory(this.getMemory().add(this.getAnswer()));
            break;
        case "M+":
            this.action();
            this.setMemory(this.getMemory().subtract(this.getAnswer()));
            break;
        case "=":
            this.action();
            break;
        case "＜":
            display.positionCaret(caretPosition-1);
            break;
        case "＞":
            display.positionCaret(caretPosition+1);
            break;
        case "MR":
            key = "mem";
        default:
            if(display.getText().isEmpty()) {
                display.appendText(key);
            } else {
                display.insertText(caretPosition, key);
            }
            if(key.equals("(,)")) {
                display.positionCaret(caretPosition-2);
            }
            break;
        }
        caretPosition = display.getCaretPosition();
    }

    public void backSpace() {
        String text = display.getText();
        if(text.isEmpty()) {
            return;
        }
        display.deleteText(caretPosition - 1, caretPosition);
    }

    @FXML
    public void action() {
        String text = display.getText();
        if(text.isEmpty()) {
            text = "0";
        }
        preDisplay.setText(text);

        try {
            BigDecimal result = calculator.calculate(text);
            text = result.toPlainString();
            this.setAnswer(result);
        } catch(IllegalStateException e) {
            text = "error";
            this.setAnswer(BigDecimal.ZERO);
        }
        display.setText(text);
        display.positionCaret(text.length());
    }

    private void setMemory(BigDecimal decimal) {
        calculator.setVariable("mem", decimal);
    }

    private BigDecimal getMemory() {
        return calculator.getVariable("mem");
    }

    private void setAnswer(BigDecimal decimal) {
        calculator.setVariable("ans", decimal);
    }

    private BigDecimal getAnswer() {
        return calculator.getVariable("ans");
    }
}
