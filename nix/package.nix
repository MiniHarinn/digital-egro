# Adapted from nixpkgs' pkgs/by-name/di/digital/package.nix, building this fork from the local source tree.
{
  lib,
  maven,
  jre,
  makeWrapper,
  makeDesktopItem,
  copyDesktopItems,
  src,
  version,
  gitRev,
  buildDate,
}:

maven.buildMavenPackage {
  pname = "digital-egro";
  inherit version src jre;

  # The "no-git-rev" profile disables the plugin which reads the version from .git,
  # which does not exist in the sandbox. The version and timestamps are passed in instead.
  # (see https://github.com/hneemann/Digital/issues/289#issuecomment-513721481)
  mvnParameters = lib.escapeShellArgs [
    "-Pno-git-rev"
    "-Dgit.commit.id.describe=${gitRev}"
    "-Dproject.build.outputTimestamp=${buildDate}"
    "-DbuildTimestamp=${buildDate}"
  ];

  # Update after changing dependencies in pom.xml: set to lib.fakeHash, build, copy the hash from the error.
  mvnHash = "sha256-qFJvpxK6PDmMfeL5fKVHUzK2NRLcQhQ3PJbwv2hYZqY=";

  nativeBuildInputs = [
    copyDesktopItems
    makeWrapper
  ];

  installPhase = ''
    runHook preInstall

    # Digital.jar contains all dependencies and expects the component libraries (74xx, ...) in lib/ next to it
    install -Dm644 target/Digital.jar $out/share/java/Digital.jar
    cp -r src/main/dig/lib $out/share/java/

    makeWrapper ${lib.getExe jre} $out/bin/digital \
      --add-flags "-jar $out/share/java/Digital.jar"

    install -Dm644 src/main/svg/icon.svg $out/share/icons/hicolor/scalable/apps/digital.svg
    for size in 16 32 48 64 128; do
      install -Dm644 src/main/resources/icons/icon"$size".png $out/share/icons/hicolor/"$size"x"$size"/apps/digital.png
    done

    runHook postInstall
  '';

  desktopItems = [
    (makeDesktopItem {
      name = "digital";
      desktopName = "Digital";
      comment = "Easy-to-use digital logic designer and circuit simulator";
      exec = "digital %f";
      icon = "digital";
      categories = [
        "Education"
        "Electronics"
      ];
      mimeTypes = [ "text/x-digital" ];
      keywords = [
        "simulator"
        "digital"
        "circuits"
      ];
    })
  ];

  meta = {
    description = "Digital logic designer and circuit simulator, with a more ergonomic editor";
    homepage = "https://github.com/MiniHarinn/Digital-egro";
    license = lib.licenses.gpl3Only;
    mainProgram = "digital";
    platforms = lib.platforms.all;
  };
}
