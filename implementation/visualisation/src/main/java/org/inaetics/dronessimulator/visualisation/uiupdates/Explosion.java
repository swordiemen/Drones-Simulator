package org.inaetics.dronessimulator.visualisation.uiupdates;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.inaetics.dronessimulator.visualisation.SpriteAnimation;

public class Explosion extends UIUpdate {
    private final double scale;
    private final ImageView imageView;

    public Explosion(double scale, ImageView imageView) {
        this.scale = scale;
        this.imageView = imageView;
    }

    @Override
    public void execute(Pane pane) {
        ImageView explosionImage = new ImageView(new Image(getClass().getResourceAsStream("/explosion.png")));
        explosionImage.setScaleX(scale);
        explosionImage.setScaleY(scale);
        explosionImage.setX(imageView.getLayoutX() - imageView.getFitWidth() / 2);
        explosionImage.setY(imageView.getLayoutY() - imageView.getFitHeight() / 2);
        SpriteAnimation explosionAnimation = new SpriteAnimation(explosionImage, Duration.millis(1000), 40, 8, 0, 0, 256, 256);
        explosionAnimation.setCycleCount(1);
        explosionAnimation.play();
        pane.getChildren().addAll(explosionImage);

        explosionAnimation.setOnFinished(
                event -> pane.getChildren().remove(explosionImage)
        );
    }
}
