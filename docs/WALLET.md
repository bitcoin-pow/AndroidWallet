# Wallet setup

An enrolled strong Android biometric (such as fingerprint) is required. Tap
Create wallet, write down all 12 words offline, confirm words 1, 6 and 12, and
authenticate to save. Receive then shows the first address. Restore accepts an
English BIP39 phrase and optional passphrase on an empty installation. Existing
wallets are never overwritten. Core wallet.dat import is not supported.

## Derivation

bitcoinj 0.16.5 supplies BIP39, BIP32 and SegWit encoding. New phrases have 128 bits
of SecureRandom entropy and an empty passphrase. Restore validates checksum and
12/15/18/21/24-word lengths. Phrase case/whitespace is normalized. Passphrases use
NFKD normalization but preserve case and whitespace; typos open different wallets
and cannot be detected.

First address: `m/84'/0'/0'/0/0`. Record this path, BTCW and any passphrase in the
offline backup. Coin type zero is an explicit app convention, not a node mandate
or a claim of recovery compatibility with other BTCW apps. BTCW uses Bitcoin's
`bc` address encoding; bitcoinj Bitcoin network services are never instantiated.
Use a phrase dedicated to BTCW rather than a funded Bitcoin phrase.

The first address now has a receiving QR code and a copy button. The QR contains
the raw address, not a Bitcoin payment URI, and has a white quiet zone for scanning.
Decode tests assert the exact encoded address. Wallet discovery scans the first
20 receiving and 20 change addresses in account zero. Address rotation and
additional accounts are not implemented.

After unlocking, leave the app visible until the block scan completes. Initial
scans and rescans begin at block 141000 inclusive; older outputs are not discovered.
Subsequent launches resume saved progress. Ordinary
received outputs become spendable after one confirmation. Send accepts a BTCW
address, amount and fee rate, then shows destination, fee, total and change for
review. Authenticate only after reviewing these details. The signer checks
ownership, previous outputs, duplicate inputs, dust, fee bounds and change;
it supports ordinary P2WPKH inputs and a maximum of 100 selected inputs. Mining
and coinstake outputs are excluded. Change uses `m/84'/0'/0'/1/0`.

Pending sends and input reservations are persisted before network submission.
Queued, peer-delivered and block-confirmed states are distinct. Delivery does
not mean mempool acceptance. Chain changes trigger a rescan and preserve locally
signed transactions for rechecking. Spending requires a complete, fresh scan.

The complete synthetic receive/sign/peer-delivery/confirm/reorg pipeline and
Send review gating pass emulator checks. Real-fund transfers and node mempool
acceptance have not been tested. See SPV.md for the lightweight verification
and single-peer limitations; this is not an audited production wallet.

## Protection

The 64-byte seed is encrypted with AES-256-GCM using Android Keystore. Each
encryption/decryption requires a BiometricPrompt CryptoObject authenticated with
a strong biometric. Hardware backing depends on the device. The 93-byte vault
contains version, IV and ciphertext/tag. Authenticated additional data binds the
BTCW genesis, version and derivation path. AtomicFile stores the vault under
noBackupFilesDir; cloud backup and device transfer are also excluded explicitly.

The mnemonic and passphrase are never persisted or copied to the clipboard.
Unlocked UI state contains only public watch addresses. Mutable seed buffers are
wiped after use; JVM Strings and bitcoinj key objects cannot be guaranteed
zeroized. Screenshots and autofill are disabled; password inputs disable
autocorrect. Secret UI state is not saved across activity recreation. Backgrounding
locks the wallet and cancels unfinished setup, so write down the phrase first.

The phrase cannot be shown again after setup: only the seed is retained. Changing
biometric enrollment may invalidate the encryption key. There is no PIN fallback.
Corrupted/inaccessible vaults are preserved and recovery requires an empty
installation with the original phrase/passphrase; replacing a saved wallet is
not implemented. Never uninstall or clear data without the recovery backup.

## Verification

Five JVM tests cover the BIP39 TREZOR seed vector, first two BIP84 receiving
addresses, invalid phrases, Unicode/whitespace handling, and generated phrases.
Android API 33 tests cover derivation, screenshot protection and the missing
biometric enrollment gate. After biometric enrollment, an isolated seed-vault
test reproduced a save failure: submitting GCM AAD before authentication cached
a Keystore authorization error. Both encryption and decryption now submit AAD
only after the biometric CryptoObject is authenticated. Authenticated saving,
reopening the vault, and decrypting back to the original test seed now pass on
the emulator. The test uses a disposable file/key and leaves wallet data alone.
Biometric key invalidation and physical-device coverage remain acceptance tests.

Opt-in regression command (supply emulator touches during both prompts):
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wallet.btcw.SeedVaultBiometricTest -Pandroid.testInstrumentationRunnerArguments.vaultBiometricTest=true`

Before real funds: on an enrolled device create a disposable wallet, confirm its
backup, save, background, reopen and unlock; compare the address after restoring
in a separate empty installation. Check canceled authentication never saves or
unlocks a wallet. Verify key invalidation recovery with that same test backup.

References:
- https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
- https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
- https://developer.android.com/identity/sign-in/biometric-auth

Settings includes **Rescan from block 141000**. This pauses spending, cancels an
unsubmitted review, and rebuilds wallet outputs and history from that block.
Headers, encrypted seed, locally signed transactions and input reservations are
preserved. The request is persisted so it survives closing the app before the
worker starts. Spending resumes after the scan catches up; ordinary outputs and
change need one confirmation, while unconfirmed inputs remain unavailable.
