# BTCW SPV implementation

Reference node: https://github.com/bitcoin-pow/BitcoinPoW

Inspected branch: `new_fork_no_ext_work_no_pools`, revision
`409eee719df3bc7c397f840ed75c173e6d02e3b5`.

## Synchronization and validation

The foreground client discovers peers using the node's DNS seeds, performs a
version/verack handshake and downloads linked headers from mainnet genesis.
All 22 pinned mainnet checkpoints are checked, including historical checkpoint
141410. A matched checkpoint anchors its ancestors, not its descendants.
Header batches are journaled in no-backup storage and rechecked on startup.
Connections, exchanges, message sizes and storage are bounded. Failed peers are
replaced; backgrounding stops the connection. Headers are polled every ten seconds
once caught up. Peer-announced height alone never establishes an anchor.

Wallet discovery downloads full blocks in batches of eight and checks each
transaction Merkle root against the corresponding header. The sampled public peer
did not advertise bloom or compact-filter support. Initial scanning starts at
block 141000 (inclusive) and can consume substantial time and data; only wallet transactions
and outputs are retained. Progress is persisted after each batch. Outputs created before block 141000 are
not discovered, even if still unspent. Header verification still starts at genesis.
Existing ledgers from the former scan policy are rebuilt once from block 141000,
preserving locally signed sends. Later starts resume saved progress; chain-change
rescans also begin at block 141000.

Beyond the matched checkpoints, the verifier checks BTCW difficulty, timestamps,
block-signature work and the coinstake signing-key relationship. It implements
historical LWMA, legacy lax DER decoding, and the node's ASERT/signature transition
at height 144444. Bitcoin's proof-of-work rules are not substituted for BTCW's.

This is lightweight verification, not full UTXO consensus execution. It does not
independently execute every transaction script or reproduce all stake ownership
and unspentness checks. The client uses one peer at a time and does not yet compare
competing chains by cumulative work across independent peers. These are material
limitations for hostile-peer and competing-chain scenarios. A mismatched branch
causes replay from genesis; wallet state is rescanned and locally signed sends
remain reserved while their chain status is rechecked.

## Wallet integration

The wallet scans the first 20 receiving and first 20 change addresses in BIP84
account zero. Ordinary received outputs become selectable after one confirmation
and a complete scan. Mining/coinstake outputs are not spendable in this app.
Send requires a fresh sync snapshot, an explicit fee/change review, biometric
seed access, and signing of the complete transaction. Inputs are durably reserved
before peer delivery. A peer pong acknowledges transport only; confirmation
requires finding the transaction in a checked block. Pending sends survive
restart and are retried on reconnect. See [wallet behavior](WALLET.md).

At startup (after header download on a fresh installation) and every ten minutes
while visible, the client compares hashes from up to ten distinct peer IPs at
the same local tip height. Each response follows linked headers from a known
checkpoint ancestor. A unique most frequent hash with at least two supporters
guides peer selection; ties or insufficient responses fall back to normal sync.
Each survey has a 35-second deadline and bounded header batches. Survey status
shows the actual number of replies. Peer votes never add or replace checkpoints
and are not a substitute for consensus validation. Distinct IPs do not guarantee
independent operators. The comparison resumes on the next startup; it is not an
Android background service.

## Verification and remaining coverage

Unit tests cover wire framing, checkpoint/header validation, journals, Merkle
checks, difficulty transitions, historical signatures and transaction signing.
Emulator checks cover synthetic receive-to-send tracking, signature verification,
loopback peer delivery, durable reservations, confirmation and reorg replay,
fee review gating and receiving QR decoding.

On 2026-09-19 a read-only live probe checked all 573 blocks from 141411 through
141983 using the production parser and verifier: Merkle roots, difficulty and
block-signature work passed. This is historical compatibility evidence, not a
full consensus audit. No real-fund transaction was broadcast. Acceptance by the
node's mempool in an end-to-end regtest scenario remains untested, as do physical
device coverage and complete recovery interoperability with other wallets.
