"""Repository / registry ports owned by the domain."""

from __future__ import annotations

from abc import ABC, abstractmethod


class UpiHandleRegistryPort(ABC):
    """Knows which UPI handles (the part after @) belong to real PSPs."""

    @abstractmethod
    def is_known(self, handle: str) -> bool: ...

    @abstractmethod
    def nearest(self, handle: str) -> tuple[str, int] | None:
        """Return (closest known handle, edit distance) or None."""

    @abstractmethod
    def bank_for(self, handle: str) -> str | None:
        """Return the bank/PSP name the handle belongs to, if known."""
