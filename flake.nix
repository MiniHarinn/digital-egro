{
  description = "Digital logic designer and circuit simulator, with a more ergonomic editor";

  # The channel tarball only advances after Hydra built it, so everything is in the binary cache
  inputs.nixpkgs.url = "https://channels.nixos.org/nixos-unstable/nixexprs.tar.xz";

  outputs =
    { self, nixpkgs }:
    let
      inherit (nixpkgs) lib;
      forAllSystems = lib.genAttrs [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];

      # lastModifiedDate is "YYYYMMDDhhmmss". Some inputs (e.g. path:) do not provide it.
      d = self.lastModifiedDate or null;
      date = "${builtins.substring 0 4 d}-${builtins.substring 4 2 d}-${builtins.substring 6 2 d}";
      time = "${builtins.substring 8 2 d}:${builtins.substring 10 2 d}:${builtins.substring 12 2 d}";

      # Only the nix files are left out, so editing them does not rebuild Digital
      src = lib.fileset.toSource {
        root = ./.;
        fileset = lib.fileset.difference ./. (
          lib.fileset.unions [
            ./flake.nix
            (lib.fileset.maybeMissing ./flake.lock)
            ./nix
          ]
        );
      };

      overlay = final: _prev: {
        digital-egro = final.callPackage ./nix/package.nix {
          inherit src;
          version = "0.31-unstable" + lib.optionalString (d != null) "-${date}";
          gitRev = self.shortRev or self.dirtyShortRev or "unknown";
          # jar entries can not be older than 1980
          buildDate = if d != null then "${date}T${time}Z" else "1980-01-01T00:00:00Z";
        };
      };

      pkgsFor = system: nixpkgs.legacyPackages.${system}.extend overlay;
    in
    {
      overlays.default = overlay;

      packages = forAllSystems (system: rec {
        digital-egro = (pkgsFor system).digital-egro;
        default = digital-egro;
      });

      apps = forAllSystems (system: {
        default = {
          type = "app";
          program = lib.getExe self.packages.${system}.default;
          meta.description = "Run Digital";
        };
      });

      devShells = forAllSystems (
        system:
        let
          pkgs = pkgsFor system;
        in
        {
          default = pkgs.mkShell {
            inputsFrom = [ pkgs.digital-egro ];
          };
        }
      );

      formatter = forAllSystems (system: nixpkgs.legacyPackages.${system}.nixfmt);
    };
}
