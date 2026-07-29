from __future__ import annotations

import sys
from unittest.mock import patch

from app.agent.controlled_evaluation_cli import _arguments


def test_cli_accepts_explicit_dry_run_mode() -> None:
    with patch.object(sys, "argv", ["controlled_evaluation_cli.py", "--dry-run", "--cases", "30"]):
        arguments = _arguments()

    assert arguments.live is False
    assert arguments.dry_run is True
