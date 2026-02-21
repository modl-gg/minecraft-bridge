Minecraft Anticheat Bridge
-

### Additional commands
Primarily for users who install modl on Velocity or Bungeecord, modl-anticheat-bridge provides the following console-only commands:  
<nl> <> - Required parameters  
[] - Optional parameters  

/anticheat-ban <player> [-lenient|-normal|-severe] [notes...]  
/anticheat-kick <player> [kick message...]

The anticheat-ban command will execute a ban for a configured punishment type (default: cheating) with notes (not shown in ban-message).
The anticheat-kick command will execute a kick and show the player whatever message you want displayed.

### Reporting
This feature currently only works with Grim and Polar. Based on configured per-check thresholds (w/a default threshold) and report cooldown (default: 60s), the anticheat will automatically create player reports (w/logs). 
Staff can link these reports to punishments as proof, will be notified of these reports across the network/on discord (no need for anticheat alert spam), and you can sort players by reports in staff menu to find the most likely cheaters.
