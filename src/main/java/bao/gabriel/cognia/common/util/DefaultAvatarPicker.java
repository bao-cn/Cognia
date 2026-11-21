package bao.gabriel.cognia.common.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DefaultAvatarPicker {

    private static final List<String> AVATARS = List.of(
            "48a79271072d142d-panda-3.png",
            "94692733c1015bc5-panda-content-closed-eyes-left.png",
            "1fea4bd8535a97d8-orca-ochre.png",
            "a920a2fecc2389ea-ring-tailed-coati.png",
            "9a40d7590a5e8b64-sheep-3.png",
            "6de3117a12e17302-wide-face-owl.png",
            "6dad62a08c5b99fb-owl-4.png",
            "9777fa26a9546560-glass-frog-2.png",
            "61b349663dab3bfb-horse-2.png",
            "a0638479ef1b7a8c-kingfisher-2.png",
            "990d02996c04e29f-colorpoint-cat-2.png",
            "123deac8ef5162a0-platypus-6.png",
            "bda4fc66c6949066-fox-2.png",
            "328f7b9a37057e72-dog-close-ears-left.png",
            "85230c03cb0b8c69-rabbit-bean-head-right.png",
            "6891c8570c66e825-miniature-schnauzer-dog-2.png",
            "f01cfda5a9e44db1-baby-zebra.png",
            "acc9908f6f730d0c-bear-compact-muzzle-left.png",
            "794515e2f94daffd-flying-squirrel.png",
            "b8825dec2a4b3eb6-maltese.png",
            "b1d797e93e12918f-bumblebee-4.png",
            "d574eb13244e7806-alpaca-7.png",
            "1bb32fb78a49993c-turkish-angora-cat-2.png",
            "89c92419c6cac531-sloth-3.png",
            "1d5809227ba96ade-doberman-dog-2.png"
    );

    private DefaultAvatarPicker() {
    }

    public static String randomPath() {
        return "/avaters/" + AVATARS.get(ThreadLocalRandom.current().nextInt(AVATARS.size()));
    }
}
